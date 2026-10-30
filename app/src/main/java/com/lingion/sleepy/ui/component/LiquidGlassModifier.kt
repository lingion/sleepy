package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import java.util.Collections

/**
 * 液态玻璃 pane 引擎 — FlClash `liquid_glass.dart` `_captureFrom`(1201-1252)
 * 的 Android 逐行等价(架构定案 2026-10-21: `Paint.setShader` 路线, 完全不碰
 * 平台 `RenderEffect`):
 *
 * ```
 * Dart 真源                                       Android(本类)
 * shader.setImageSampler(0, atlas, low)     →     shader.setInputShader("uTexture", BitmapShader)
 * paint..shader = shader                          paint.shader = runtimeShader
 * canvas.drawPath(outline, paint)             →   nativeCanvas.drawPath(clip.asAndroidPath(), paint)
 * ```
 *
 * **API 31 起全功能**:`RuntimeShader` 与 `Shader.setInputShader` 都是 API 31
 * 公开面(android-33 jar javap 实锤), 33 才有的只是 `RenderEffect.createRuntimeShaderEffect`
 * — 本路线不用它。华为 API 31 因此与 FlClash(Flutter 自带 Skia 管线, 同样不经
 * 平台 RenderEffect)行为一致。全部 blur/折射/色散都由 AGSL 自己做:
 * `sampleFrosted` 的 64-tap Vogel 圆盘即高斯(uTexels.y=0 时 σ=全量 frost, 数学
 * 1:1)。FlClash 的 atlas 预糊只是性能优化, 美学优先定则下不采用。
 *
 * **每 Pane 独占一个 RuntimeShader 实例**(≠ FlClash `_GlassProgram` 全局单例):
 * HWUI 把 paint-shader **按引用**录制进 RenderNode, 到 RenderThread 重放时才栅格
 * 化 — uniform/sampler 存于 shader 对象(录制不快照), 故同一实例在同一节点内两次
 * drawPath 会串成最后一次设置的 uniforms(pill 的 sharp+haze 两遍若共用一个实例,
 * 重放时两遍都变 haze)。FlClash 以每遍独立 Picture 规避; 等价 = 实例隔离。
 * 每 pane 每帧只在自身 draw 前变更一次, 帧内自洽; 单线程主线程设置。
 *
 * 坐标契约(装配见 [GlassUniforms.build], 采样映射见 [GlassUniforms.sampling]):
 *  - fragCoord = 当前绘制节点**局部设备 px**(未经本节点 graphicsLayer 缩放):
 *    `main` 里 `point = uFrame.xy + fragCoord*uFrame.zw`, uFrame=(−rectOriginPx,
 *    −rectOriginPx, 1/density, 1/density) 把 (0,0) 原点 clip 折回 pane 本地 dp
 *    (shader 的 shape/edge/normal 全以 pane 左上为原点 = FlClash FlutterFragCoord
 *    −rect 同义)。draw 前 translate(rect.left, rect.top) 与 uFrame.xy 补偿配对。
 *  - backdropBitmap(atlas)左上角 [atlasOrigin] = pane 身后内容的捕获原点
 *    (root px); pane 在 atlas 内的偏移由 uSample.xy(pad, 消费方算好)编码,
 *    uClamp = atlas 全幅像素中心(折射越界即钳边 = capture intersect(bounds))。
 *
 * pill 的 haze 双槽 [mix](Dart `mix(base, sampleHaze, share)`) = 两个 pane 两遍
 * drawPath 的 SRC_OVER 等价式: pass1 sharp(pillPane, uHaze=0, tint/rim/glow 在场,
 * α1 铺满), pass2 同 atlas 但 uShape.w=hazeBlur(dp, shader 自糊)、tint.a/hairline/
 * rimBlur/rim/glow 场关闭、α=share(hazePane) → share·haze+(1−share)·sharp。
 */
/**
 * 玻璃 shader 后端的两条方言(见 [LiquidGlassEngine]):
 *  - [ModernGlassRuntime] API≥33: 公开 1-arg `RuntimeShader(text)` +
 *    setFloatUniform/setInputShader(字面调用, 与历史行为逐字节一致)。
 *  - [LegacyGlassRuntime] API 31/32: 该设备(Huawei TAS-AN00, SDK 31)的
 *    `android.graphics.RuntimeShader` 只有 `(String, boolean)` ctor(2021 原型期),
 *    且跑旧 SkSL 方言 —— AGSL 的 `.eval()` 采样被拒("cannot swizzle value of
 *    type 'shader'"), 唯一能编译的 child-shader 采样是 `sample(child, coord)`。
 *    故走 2-arg ctor(反射)+ setUniform(1f/2f/float[] 三形态)+ setInputShader,
 *    shader 文本须先经 [toLegacyDialect] 变换。
 */
private interface GlassRuntime {
    /** 交给 `paint.shader` 的平台 Shader 句柄。 */
    val androidShader: Shader
    fun setUniforms(v: FloatArray)
    fun setInputShader(name: String, shader: Shader)
    fun release() {}
}

/** API≥33 直通: 现有字面 setFloatUniform 序列零改动(仿真机 golden path)。 */
private class ModernGlassRuntime(private val shader: RuntimeShader) : GlassRuntime {
    override val androidShader: Shader get() = shader

    /** AGSL uniform 装配 — 顺序/分段 = [GlassUniforms] 209 表(_captureFrom 1210-1219)。 */
    override fun setUniforms(v: FloatArray) {
        with(shader) {
            setFloatUniform("uSize", v[0], v[1])
            setFloatUniform("uFrame", v[2], v[3], v[4], v[5])
            setFloatUniform("uSample", v[6], v[7], v[8], v[9])
            setFloatUniform("uClamp", v[10], v[11], v[12], v[13])
            setFloatUniform("uTexels", v[14], v[15])
            setFloatUniform("uHazeSample", v[16], v[17], v[18], v[19])
            setFloatUniform("uHazeClamp", v[20], v[21], v[22], v[23])
            setFloatUniform("uHaze", v[24])
            setFloatUniform("uShape", v[25], v[26], v[27], v[28])
            setFloatUniform("uCorners", v[29], v[30], v[31], v[32])
            setFloatUniform("uCurves", v.copyOfRange(33, 81))
            setFloatUniform("uOptics", v[81], v[82], v[83])
            setFloatUniform("uTint", v[84], v[85], v[86], v[87])
            setFloatUniform("uLight", v[88], v[89], v[90])
            setFloatUniform("uGlow", v[91], v[92], v[93], v[94])
            setFloatUniform("uGlowSpread", v[95], v[96])
            setFloatUniform("uEdge", v[97], v[98], v[99])
            setFloatUniform("uRim", v[100], v[101], v[102], v[103])
            setFloatUniform("uOutlined", v[104])
            setFloatUniform("uProfile", v.copyOfRange(105, 169))
            setFloatUniform("uShineTurns", v.copyOfRange(169, 177))
            setFloatUniform("uShineLit", v.copyOfRange(177, 185))
            setFloatUniform("uShineEnds", v.copyOfRange(185, 197))
            setFloatUniform("uShineShares", v.copyOfRange(197, 209))
        }
    }

    override fun setInputShader(name: String, shader: Shader) {
        this.shader.setInputShader(name, shader)
    }
}

/**
 * API 31/32 反射后端。ctor=`(String,boolean)`; uniform 面只有
 * setUniform(String,float)/(String,float,float)/(String,float[]) 与
 * setInputShader(String,Shader)(真机反射枚举实测, 无 setFloatUniform、无 4-arg)。
 * [UNIFORMS] 顺序累加偏移 == [GlassUniforms] 段起点(0..209), 与 [ModernGlassRuntime]
 * 字面索引一一对齐(2·4·4·4·2·4·4·1·4·4·48·3·4·3·4·2·3·4·1·64·8·8·12·12 = 209)。
 * 任一反射步失败 → 构造抛异常, 由 [LiquidGlassEngine.newPane] 收敛为 null pane。
 */
private class LegacyGlassRuntime(text: String, isOpaque: Boolean) : GlassRuntime {
    private val instance: Any
    private val u1: Method
    private val u2: Method
    private val uArr: Method
    private val inShader: Method

    init {
        val cls = Class.forName("android.graphics.RuntimeShader")
        val ctor: Constructor<*> =
            cls.getDeclaredConstructor(String::class.java, Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
        instance = ctor.newInstance(text, isOpaque)
        u1 = resolve(cls, "setUniform", String::class.java, Float::class.javaPrimitiveType)
        u2 = resolve(
            cls, "setUniform", String::class.java,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
        )
        uArr = resolve(cls, "setUniform", String::class.java, FloatArray::class.java)
        inShader = resolve(cls, "setInputShader", String::class.java, Shader::class.java)
    }

    override val androidShader: Shader get() = instance as Shader

    override fun setUniforms(v: FloatArray) {
        var off = 0
        for ((name, count) in UNIFORMS) {
            val seg = when (count) {
                1 -> { u1.invoke(instance, name, v[off]); off++; continue }
                2 -> { u2.invoke(instance, name, v[off], v[off + 1]); off += 2; continue }
                else -> v.copyOfRange(off, off + count).also { off += count }
            }
            uArr.invoke(instance, name, seg)
        }
    }

    override fun setInputShader(name: String, shader: Shader) {
        inShader.invoke(instance, name, shader)
    }

    companion object {
        /** 公开面优先, 回退到声明面(@SystemApi/hidden 时 getMethod 抛 NoSuchMethod)。 */
        private fun resolve(
            cls: Class<*>, name: String, vararg types: Class<*>?,
        ): Method = runCatching { cls.getMethod(name, *types) }
            .recoverCatching { cls.getDeclaredMethod(name, *types).apply { isAccessible = true } }
            .getOrThrow()

        /** (uniform 名, float 个数), 顺序 == shader 声明顺序 == [GlassUniforms] 索引。 */
        private val UNIFORMS = listOf(
            "uSize" to 2, "uFrame" to 4, "uSample" to 4, "uClamp" to 4,
            "uTexels" to 2, "uHazeSample" to 4, "uHazeClamp" to 4, "uHaze" to 1,
            "uShape" to 4, "uCorners" to 4, "uCurves" to 48, "uOptics" to 3,
            "uTint" to 4, "uLight" to 3, "uGlow" to 4, "uGlowSpread" to 2,
            "uEdge" to 3, "uRim" to 4, "uOutlined" to 1, "uProfile" to 64,
            "uShineTurns" to 8, "uShineLit" to 8, "uShineEnds" to 12,
            "uShineShares" to 12,
        )
    }
}

class LiquidGlassEngine private constructor(
    private val shaderText: String,
    private val legacy: Boolean,
) {

    companion object {
        private const val TAG = "DockGlass"

        /**
         * AGSL → API-31 旧 SkSL 方言的两处机械变换(真机实测仅这两处即整段编译通过):
         *  ① `uTexture.eval(x)` → `sample(uTexture, x)` —— 旧方言无 `.eval()` 方法语法
         *    (被当 swizzle 拒), child-shader 采样只能写成全局 `sample(child, coord)`。
         *  ② `i < kFrostTaps` → `i < 64` —— 旧方言要求循环界是字面量/字面量表达式,
         *    不接受 top-level `const int`(而 `float(kFrostTaps)` 当值用不受影响)。
         * 二者都是方言语法适配, 数学/分支/常量逐字保留(1:1 FlClash 端口不变)。
         */
        fun toLegacyDialect(text: String): String =
            text.replace("uTexture.eval(", "sample(uTexture, ")
                .replace("i < kFrostTaps", "i < 64")

        /** API 31/32: 反射 2-arg ctor 是否可达(探测成功即可用)。 */
        private val legacyAvailable: Boolean
            get() = runCatching {
                Class.forName("android.graphics.RuntimeShader")
                    .getDeclaredConstructor(
                        String::class.java, Boolean::class.javaPrimitiveType,
                    ).isAccessible = true
            }.isSuccess

        /** API<31 → null; 31/32 → 反射 legacy(探测不可达则 null); ≥33 → 公开 1-arg。 */
        @Composable
        fun rememberEngine(): LiquidGlassEngine? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            if (legacy && !legacyAvailable) {
                Log.w(TAG, "API${Build.VERSION.SDK_INT} RuntimeShader 反射 ctor 不可达 → classic 底")
                return null
            }
            val ctx = LocalContext.current
            return remember(ctx) {
                val agsl = runCatching {
                    ctx.assets.open("shaders/liquid_glass.agsl").reader().readText()
                }.getOrNull()
                val text = agsl?.let { if (legacy) toLegacyDialect(it) else it }
                runCatching {
                    text?.let { LiquidGlassEngine(it, legacy) }
                }.onFailure { Log.w(TAG, "AGSL 资产不可读 → classic 底: ${it.message}") }
                    .getOrNull()
            }
        }
    }

    fun newPane(name: String = "LiquidGlassPane"): Pane? =
        runCatching { Pane(shaderText, legacy, name) }
            .onFailure { Log.w(TAG, "$name shader 编译失败 → classic 底: ${it.message}") }
            .getOrNull()

    /** @param replace 内圈 lensContent 用 SRC(Dart capture blendMode=src 1645)。 */
    class Pane internal constructor(text: String, legacy: Boolean, private val name: String) {
        // legacy=true 用反射 2-arg ctor(isOpaque=false: 玻璃输出带 coverage α, 非不透明);
        // modern 用公开 1-arg ctor(行为逐字节不变)。
        private val rt: GlassRuntime =
            if (legacy) LegacyGlassRuntime(text, isOpaque = false)
            else ModernGlassRuntime(RuntimeShader(text))
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        /** 折射主 pane: `paint.shader = runtimeShader; canvas.drawPath(clip, paint)`。
         *  [uniforms] 209-float 由 [GlassUniforms.build] 装配(paneOriginPx=rect.left/top)。
         *  [canvas] 提供 = 带重放(GraphicsLayer.record 的 DrawScope 在协程, 无节点);
         *  缺省 = 本 scope(现有实屏调用逐字节不变)。 */
        fun drawBackdropShader(
            scope: DrawScope,
            backdropBitmap: Bitmap,
            uniforms: FloatArray,
            rect: Rect,
            clip: Path,
            alpha: Float = 1f,
            replace: Boolean = false,
            canvas: Canvas = scope.drawContext.canvas,
        ) {
            drawPatch(canvas, backdropBitmap, uniforms, rect, clip, alpha, replace)
        }

        /** haze 叠层遍(pill 双槽的第二遍): 同一 atlas, uShape.w=[sigmaDp](dp,
         *  shader sampleFrosted 圆盘即高斯 — 无平台 blur), tint/hairline/rimBlur/
         *  rim/glow 场关闭, α=[alpha]=share。**须用与 sharp 遍不同的 pane 实例**
         *  (见类头 HWUI 实例隔离)。 */
        fun drawBlurBackdrop(
            scope: DrawScope,
            backdropBitmap: Bitmap,
            uniforms: FloatArray,
            sigmaDp: Float,
            alpha: Float,
            rect: Rect,
            clip: Path,
            canvas: Canvas = scope.drawContext.canvas,
        ) {
            if (sigmaDp <= 0f || alpha <= 0f) return
            val patched = uniforms.copyOf()
            patched[GlassUniforms.I_SHAPE + 3] = sigmaDp
            patched[GlassUniforms.I_TINT + 3] = 0f
            patched[GlassUniforms.I_EDGE] = 0f
            patched[GlassUniforms.I_EDGE + 2] = 0f
            patched[GlassUniforms.I_LIGHT] = 0f
            // 审计(两轮一致): uRim.w 未清零 → agsl 385 lit 仍真(光模式
            // hairlineSide=.35), 483 darken=mix(0,.35,·) 在 haze 叠层再画一次
            // 侧发丝线 = 双绘晕。uLight.y 同零(line 宽度参数, lit 死后死码,
            // 但按源码断言归零防 shine 带宽 387/471 误读)。
            patched[GlassUniforms.I_RIM + 3] = 0f
            patched[GlassUniforms.I_LIGHT + 1] = 0f
            patched[GlassUniforms.I_GLOW + 2] = 0f
            patched[GlassUniforms.I_GLOW + 3] = 0f
            drawPatch(canvas, backdropBitmap, patched, rect, clip, alpha, replace = false)
        }

        private fun drawPatch(
            canvas: Canvas,
            backdropBitmap: Bitmap,
            uniforms: FloatArray,
            rect: Rect,
            clip: Path,
            alpha: Float,
            replace: Boolean,
        ) {
            if (backdropBitmap.width < 1 || backdropBitmap.height < 1) return
            if (rect.width < 1f || rect.height < 1f) return
            val nc = canvas.nativeCanvas
            val bitmapShader = BitmapShader(
                backdropBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP,
            )
            rt.setUniforms(uniforms)
            rt.setInputShader("uTexture", bitmapShader)
            paint.shader = rt.androidShader
            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            if (replace) {
                paint.xfermode = SRC
            } else {
                paint.xfermode = null
            }
            nc.save()
            nc.translate(rect.left, rect.top)
            nc.drawPath(clip.asAndroidPath(), paint)
            nc.restore()
        }

        fun dispose() {
            paint.shader = null
        }
    }
}

private val SRC = PorterDuffXfermode(PorterDuff.Mode.SRC)
private val loggedKeys = Collections.synchronizedSet(HashSet<String>())
private fun wLog(msg: String) {
    if (loggedKeys.add(msg)) Log.w("DockGlass", msg)
}

/**
 * 身后内容捕获(atlas)— FlClash `LiquidGlassHost._snapshot` 的合成期捕获语义:
 * 内容层每帧 record 进 [androidx.compose.ui.graphics.layer.GraphicsLayer],
 * `toImageBitmap()` 读回上一帧 → 本 holder。bitmap 替换 = draw-scope 订阅
 * (绘制帧读, 不触发重组); [originRootPx] = atlas 左上的 root px(_publish 的
 * slot.place 原点)。
 *
 * 一帧滞后 = FlClash "pane 读 until the next pass of the host 的 atlas" 同源。
 */
class GlassBackdropHolder {
    /** draw lambda 内读 → 订阅重绘(非重组)。 */
    var bitmap: android.graphics.Bitmap? by mutableStateOf(null)
    var originRootPx: Offset by mutableStateOf(Offset.Zero)
}
