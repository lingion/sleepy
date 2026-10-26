[evidence=A] 抓取 2026-09-11 · 源URL: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584

[evidence=A] 抓取 2026-09-11 · 源URL: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584

开发
分发
推广与变现
联系我们
[搜索]
文档
管理中心
[通知]
登录/注册
[search]
开发文档[]
[]应用开发
[]快应用开发
[]小游戏开发
[]Agent生态
开发文档/应用开发/系统适配/小部件适配/小部件技术规范与系统能力说明/
小部件技术规范与系统能力说明更新时间：2024-10-17 18:16:01
一、版本更新说明
  ----------------------- ------------------------------------------ -----------------------
  版本                    更新特性                                   更新时间
  v1.0.7                  · 技术规范                                 2022-8-19
                          小部件版本号                               
                          小米Widget 大屏适配                        
                          · 小米小部件系统能力                       
                          Push透传刷新服务                           
  v1.0.6                  · 技术规范                                 2021-9-13
                          页面跳转规范                               
                          小米Widget 布局规范                        
                          注意事项                                   
                          · 小米小部件系统能力                       
                          调起小米Widget 详情页                      
  v1.0.5                  · 技术规范                                 2021-8-26
                          页面跳转规范                               
                          注意事项-小部件名称label                   
                          应用清除数据适配                           
                          · 小米小部件系统能力                       
                          跳转小米Widget 详情页-新增可传自定义参数   
                          判断是否支持小米Widget 详情页              
                          小米Widget 与 Activity 切换动画            
  v1.0.4                  · 技术规范                                 2021-7-5
                          小米Widget 布局兼容适配                    
                          注意事项                                   
  v1.0.3                  · 技术规范                                 2021-6-23
                          小米Widget 独立进程                        
                          尺寸适配                                   
                          · 小米小部件系统能力                       
                          判断是否支持小米Widget                     
  v1.0.2                  · 小米小部件系统能力                       2021-5-26
                          修改跳转 Widget 商店里的详情页             
                          修改设置卡片状态                           
  v1.0.1                  · 技术规范                                 2021-5-6
                          修改刷新机制                               
                          修改小米Widget 标识                        
                          新增小米Widget 数据恢复适配                
                          新增页面跳转规范                           
                          新增布局规范                               
                          · 小米小部件系统能力                       
                          修改跳转 Widget 商店里的详情页             
                          修改设置卡片状态                           
                          修改跳转编辑页                             
                          新增判断是否支持小米Widget                 
  v1.0.0                  初始版本                                   2021-4-21
  ----------------------- ------------------------------------------ -----------------------
二、简介
小米小部件基于原生 Android Widget，开发一个小米小部件和开发一个原生 Android Widget 基本一致，部分区别如下：
- 为了保证整机系统用户体验，小米小部件对开发者做了一些规范要求；
- 为了拓展小部件能力，丰富可玩性，小米小部件额外提供了一些调用能力；
三、小部件技术规范和要求
开发者基于小米小部件体系开发 Widget 时，需要满足下面的技术要求：
1、小米Widget 使用独立进程
1.1、描述
为了方便管理 Widget，减少 Widget 更新对后台内存的占用，提升整机的用户体验。小米小部件规定 Widget 需要使用独立后台进程（后简称：Widget进程）来进行内容更新。
1.2、Widget 进程限制说明
- Widget 进程名字为" :widgetProvider "
- Widget 进程不能拉起其他任意进程（包括 App 主进程），同时系统也会进行相应限制
- Widget 进程内存占用不能超过 35M，执行命令( adb shell dumpsys meminfo 进程名 ) 可获取进程所占内存
- Widget 进程严禁使用 native方法（fork）拉起进程
- Widget 进程只能运行 Widget内容准备和刷新相关的逻辑
1.3、Widget 进程配置说明
<receiver android:name="com.miui.ExampleWidgetProvider"
    android:process=":widgetProvider">
    ....
</receiver>
<service android:name="com.miui.ExampleWidgetService"
    android:process=":widgetProvider" >
    ....
</service>
<provider android:name="com.miui.demo.ExampleWidgetProvider"
    android:process=":widgetProvider">
    ...
</provider>
备注：Activity不需要放在 Widget 进程中
1.4、播放器小部件适配独立进程
播放器小部件存在后台播放音频以及拉起其他进程的情况，这种特殊小部件可以运行在非 Widget 进程，但必须关闭曝光刷新，必须使用前台Service，更新UI时使用主动刷新（详细可看“小米小部件系统能力说明-App主动刷新小部件”）。
1.5、其他
Widget 进程系统分配的 adj 值较高，在系统资源不足时，容易被系统回收。
2、小米Widget 曝光刷新适配
2.1、描述
小米Widget 将会去掉系统原有的定时刷新。用户滑动到有 Widget 的页面，系统会判定需要触发一次刷新并通知应用。默认小米Widget 曝光不刷新，有曝光刷新需求的小米Widget 需在 AndroidManifest.xml 中申请，并设定曝光刷新间隔miuiWidgetRefreshMinInterval（在间隔时间内曝光多次只会触发一次），曝光刷新间隔最短为10秒。
2.2、适配方式
<receiver android:name="com.miui.ExampleWidgetProvider" >
    ...
// 定义是否需要曝光刷新
    <meta-data                
    android:name="miuiWidgetRefresh"
    android:value="exposure" />
// 定义曝光刷新的时间间隔
    <meta-data
    android:name="miuiWidgetRefreshMinInterval"
// 时间单位为毫秒                    
    android:value="20000" />
// MIUI Widget 标识
    <meta-data
    android:name="miuiWidget"
    android:value="true" />
    <intent-filter>   
//系统定时刷新             
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
//MIUI展现刷新
        <action android:name="miui.appwidget.action.APPWIDGET_UPDATE" />    </intent-filter>
</receiver>
public class ExampleWidgetProvider extends AppWidgetProvider {
    ...
@Override
public void onReceive(Context context, Intent intent) {
if ("miui.appwidget.action.APPWIDGET_UPDATE".equals(intent.getAction())) {
int[] appWidgets = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            onUpdate(context, AppWidgetManager.getInstance(context), appWidgets); //或者自定义刷新逻辑
        } else {
super.onReceive(context, intent);
        }
    }
}
3、小米Widget 尺寸设置
3.1、描述
小米Widget支持 2*2、4*2、4*4三种尺寸，开发者可以根据组件信息和所需展示的功能选择合适的尺寸进行设计，并且在 resource 配置文件中指定具体的尺寸。
3.2、适配方式
每个小部件必须定义 minWidth 和 minHeight，表示默认情况下应占用的最小空间量。当用户向AppWidgetHost（如桌面，负一屏等）添加小部件时，小部件占用的宽度和高度通常会超过您指定的最小值。AppWidgetHost 为用户提供了一个可用空间网格，供用户放置小部件和图标，网格可能因设备而异。
添加小部件后，它将在水平和垂直方向进行拉伸，占用满足其 minWidth 和 minHeight 约束条件所需的最小单元格数。
虽然单元格的宽度和高度以及应用到小部件的自动外边距量可能会因设备而异，但您设置尺寸时可以参考下表中的建议大小。
  ------ ------------------------ -------------------------
  规格   建议minWidth（单位dp）   建议minHeight（单位dp）
  2*2    110                      110
  4*2    300                      110
  4*4    300                      250
  ------ ------------------------ -------------------------
3.3、示例
根据 Widget 尺寸大小在上表中选择对应的dp值，填到对应配置文件里的 minWidth/minHeight 属性。下面以2x2的 Widget 为例，说明尺寸适配过程。
// step1:在AndroidManifest.xml中声明widegt配置文件
<receiver android:name=".ExampleAppWidgetProvider" >
   ....
    <meta-data android:name="android.appwidget.provider"
    android:resource="@xml/example_appwidget_info" />
</receiver>
// step2: example_appwidget_info中设置尺寸属性
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="@dimen/widget_min_width"
    android:minHeight="@dimen/widget_min_height"
    ....    
</appwidget-provider>
// step3: values/dimens中定义尺寸值
<dimen name="widget_min_width">110dp</dimen>
<dimen name="widget_min_height">110dp</dimen>
4、小米Widget 标识
4.1、描述
在 AndroidManifest.xml文件中，小米 Widget 的receiver组件需要添加小米标识，只有添加小米标识的Widget才享有小米Widget 能力。
4.2、示例
<receiver
    android:name="com.miui.widgetdemo.provider.ExampleWidgetProvider"
    android:process=":widgetProvider">
   <meta-data
    android:name="miuiWidget"
    android:value="true" />
    ....
</receiver>
5、小米Widget 数据恢复适配
5.1、描述
Android 系统提供了 onRestored 方法用于数据从云端备份恢复时的 Widget 配置迁移。在此基础之上，小米Widget 新增了一个 Widget 配置迁移的时机。开发者无需关心何时回调，只需将新的WidgetId与数据绑定并更新UI。
注意：当小米 Widget 卡片有配置信息且存在多张卡片的配置信息不一致时需要进行相应适配（例如股票卡片，用户可以添加多张卡片，且每张卡片展示不同的股票），其他情况无需适配。
5.2、示例
public class ExampleWidgetProvider extends AppWidgetProvider {
@Override
public void onRestored(Context context, int[] oldWidgetIds, int[]
    newWidgetIds) {
super.onRestored(context, oldWidgetIds, newWidgetIds);
       onIdRemap(oldWidgetIds, newWidgetIds, null);
    }
@Override
public void onAppWidgetOptionsChanged(Context context, AppWidgetManager
     appWidgetManager, int appWidgetId, Bundle newOptions) {
super.onAppWidgetOptionsChanged(context, appWidgetManager,
         appWidgetId, newOptions);
// 小米Widget 新增配置迁移时机
if (newOptions.getBoolean("miuiIdChanged") &&  
                !newOptions.getBoolean("miuiIdChangedComplete")) {
             onIdRemap(newOptions.getIntArray("miuiOldIds"),
             newOptions.getIntArray("miuiNewIds"), newOptions);              
         }
    }
private void onIdRemap(int[] oldWidgetIds, int[] newWidgetIds, Bundle options) {
int length = oldWidgetIds.length;
for (int i = 0; i < length; i++) {   
int newWidgetId= newWidgetIds[i];
            RemoteViews views = new RemoteViews(context.getPackageName(),    
            R.layout.appwidget_provider_layout);
//开发者进行数据迁移，并完成新的数据获取
            ...
//以上操作完成后，先调用updateOptions，再调用updateAppWidget
if(options != null) {
//这一步必须执行
                options.putBoolean("miuiIdChangedComplete", true);                             
    AppWidgetManager.getInstance(context)
                .updateAppWidgetOptions(newWidgetId, options);
            }
            AppWidgetManager.getInstance(context)
            .updateAppWidget(newWidgetId, views);
        }
    }
}
6、页面跳转规范
6.1、描述
点击小米Widget 跳转应用页面时，推荐使用 PendingIntent 设置相应的 Activity 进行跳转。若业务有分发逻辑可以使用Activity进行中转。
不建议使用PendingIntent 启动BroadcastReceiver/Service，然后在BroadcastReceiver/Service里面启动 Activity。
6.2、示例
// step1: 构建跳转页面的 PendingIntent
Intent intent = new Intent(context, ExampleActivity.class);
PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);// step2: 生成 RemoteViews 关联 PendingIntent
RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.appwidget_provider_layout);
views.setOnClickPendingIntent(R.id.button, pendingIntent);// step3: 关联 widget 和 RemoteViews
appWidgetManager.updateAppWidget(appWidgetId, views);
7、小米Widget 布局规范
7.1、描述
系统通过固定的ID找到相应控件并添加圆角，保证所有小米Widget圆角统一。因此开发者需要在小部件的根布局上声明ID为"@android:id/background"。由于小米Widget 切换页面的动画使用到了背景色，因此根布局
必须有背景色且不能为全透明。
7.2、示例
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    ...
// 颜色值为示例
    android:background="#fff"
    android:id="@android:id/background">
    ...
</LinearLayout>
8、小米Widget 布局兼容适配
8.1、描述
为了让用户有更好的体验，小米Widget 需要保证以下场景正常显示：
- 负一屏、默认布局下的桌面正常显示
- 桌面图标行列数变化后正常显示 需保证桌面4x6、5x6网格体系都能正常显示（设置方式：设置—桌面—桌面布局）
- 桌面搜索框变化时正常显示 需保证有搜索框、无搜索框都能正常显示（设置方式：设置—桌面—桌面搜索框）
- 桌面虚拟键变化时正常显示 需保证有虚拟键、无虚拟键都能正常显示（设置方式：设置—桌面—系统导航方式）
- 1k、2k屏幕手机都能正常显示
为了使卡片在各种场景下都有较好的展现，需要做以下适配：
- 小米Widget 根布局宽高必须使用match_parent， 内容区需要在根布局里居中
- 内容区控件尽量不要使用绝对尺寸和绝对位置，可以使用RelativeLayout以及LinearLayout的layout_weight等控制控件的位置和尺寸
[上传文件]
8.2、示例
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@android:id/background"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    <!--widget_root_padding 与设计图保持一致，如示例图为40px-->
    android:padding="@dimen/widget_root_padding"
    android:gravity="center">
     <!--内容区-->
    <LinearLayout
        android:id="@+id/widget_content"
        android:layout_width="match_parent"
        android:orientation="vertical"
        android:layout_height="match_parent">
    </LinearLayout>   
</FrameLayout>
9、应用清除数据适配
9.1、描述
用户在使用过程中可能存在清除应用数据的行为，用户清除数据时小米系统会给小米Widget 发送刷新广播，此时应用处在无数据或未授权状态，开发者需要在该时机将小部件恢复成默认视图或授权视图。
9.2、示例
<receiver android:name="com.miui.ExampleWidgetProvider" >
    ...
// MIUI Widget 标识
    <meta-data
    android:name="miuiWidget"
    android:value="true" />
    <intent-filter>   
//MIUI展现刷新
        <action android:name="miui.appwidget.action.APPWIDGET_UPDATE" />    
    </intent-filter>
</receiver>
public class ExampleWidgetProvider extends AppWidgetProvider {
    ...
@Override
public void onReceive(Context context, Intent intent) {
if ("miui.appwidget.action.APPWIDGET_UPDATE".equals(intent.getAction())) {
int[] appWidgets = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
// 根据应用自身逻辑更新视图
            ...
        } else {
super.onReceive(context, intent);
        }
    }
}
10、小部件版本号
10.1、描述
小米小部件增加版本号，方便小部件版本迭代管理。小部件版本号针对应用内所有小部件，而非单个。当应用内任一小部件发生功能变动或新增小部件时需升级小部件版本号。若小部件较前一应用版本未发生变动，则无需更改小部件版本号。
注意：
· 名称为"miuiWidgetVersion" 的meta-data标签，应放置在application下而不是某个小部件provider下。
· 小部件发生变动或新增时一定要修改小部件版本，否则存在应用所有小部件被下线的风险。
· 小部件版本号为整数，从1开始，同应用版本号的升级一致，都只能升高。
10.2、示例
AndroidManifest.xml 文件参考如下配置：
<application
...
<meta-data
    android:name="miuiWidgetVersion"
    android:value="1" />
...
</application>
11、小米Widget 大屏适配
11.1、描述
为了使小米Widget可以在大屏设备（折叠屏，平板）上提供更好的用户体验，需要对小米Widget在大屏幕设备上进行适配。
11.2、编辑页适配
若开发者在小米Widget中设置了编辑页，大屏设备上适配后的展示效果如下图所示：
[上传文件]
适配方法：
· 对于小部件视图树上，所有可以通过点击调起编辑页面的视图控件，需要在代码中进行以下调用：
public class ExampleWidgetProvider extends AppWidgetProvider {
    ...
@Override
public void onUpdate(Context context, AppWidgetManager
        appWidgetManager, int[] appWidgetIds) {
for (int appWidgetId : appWidgetIds) {          
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_ui);
// 编辑页适配
            val widgetOptions = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
//由host提供，业务直接读取，以判断当前设备是否支持大屏预览模式
if (widgetOptions != null && widgetOptions.getBoolean("miuiLargeScreenDevice", false)) {
//如果当前设备支持大屏预览模式，则先生成一个Bundle实例，再分别执行步骤1,2
                val largeScreenOptions = Bundle().apply {
//1. 传入widgetId
                    putInt("miuiWidgetId", widgetId)                                      
                }
//2. 通过setBundle方法，调用控件的supportLargeScreenEditPreviewMode方法，并传入以上生成的Bundle
//R.id.item_container_1 为一个示例，所有点击事件为调起编辑页的控件，都必须进行类似的调用
                remoteViews.setBundle(R.id.item_container_1, "supportLargeScreenEditPreviewMode", largeScreenOptions)
            }    
            appWidgetManager.updateAppWidget(widgetId, views);
        }
        ....
    }
}
· 如果业务小部件有长按编辑入口，则在生成编辑页面链接时，需要新增miuiWidgetId参数：
//示例：public class ExampleWidgetProvider extends AppWidgetProvider {
@Override
public void onUpdate(Context context, AppWidgetManager
        appWidgetManager, int[] appWidgetIds) {
for (int appWidgetId : appWidgetIds) {          
            Bundle options =   
            appWidgetManager.getAppWidgetOptions(appWidgetId);
            String path =
"widgetdemo://com.miui.widgetdemo/widget/WidgetEditActivity";
            Uri.Build uriPath = Uri.parse(path).buildUpon();
//本次新增参数，host依据此参数来判断，业务编辑页是否支持大屏预览模式
            uriPath.appendQueryParameter("miuiWidgetId", widgetId.toString());
            options.putString("miuiEditUri", uriPath);
            appWidgetManager.updateAppWidgetOptions(appWidgetId, options);
            RemoteViews views =   
new RemoteViews(context.getPackageName(),
            R.layout.widget_ui);
            appWidgetManager.updateAppWidget(widgetId, views);
        }
        ....
    }
}
· 业务侧在编辑页面Activity的onCreate方法中，需要从Intent中解析对应参数，判断是否以大屏预览模式展示，并对大屏预览模式下的编辑页面进行改造：
public class WidgetEditActivity extends AppCompatActivity {
@Override
protected void onCreate(Bundle savedInstanceState) {
        val intent = getIntent()
//是否以大屏预览模式展示
if (intent.getBooleanExtra("isLargeScreenMode", false)) {
//该字段返回一个矩形区域，代表小部件在屏幕坐标系上的具体位置
            val rect = intent.getParcelableExtra("miuiWidgetScreenBound")
if (rect != null) {
//业务需要根据rect提供的小部件位置信息，来计算业务内容视图的具体展示位置：
//1、业务内容视图应当始终展示在小部件的左侧，或者右侧，距离小部件固定间距
//2、业务内容视图应当在小部件的左侧和右侧中，选择空间较大的一侧展示
            }
        }
    }
}
需要注意的事项：
· 不要在生成PendingIntent时，put自定义的Parcelable对象或者Serializable对象，这样会导致Host处理intent时因找不到类而抛异常。请转换成基础数据类型进行传递。
· 请不要在生成PendingIntent时，添加FLAG_IMMUTABLE，否则业务将无法解析到Host添加的参数，android s以上可以换成FLAG_MUTABLE。
· 编辑页面打开时，不允许横竖屏旋转（需要支持横竖屏，只是不允许动态旋转）。
· 业务只需要负责编辑页面内容区域（如上图示中的白色背景覆盖区域）的展示，背景高斯以及小部件的预览图部分，均由Host实现。
11.3、布局适配
在大屏设备上，负一屏和桌面对业务小部件布局进行了全局缩放，因此一般情况下，不需要业务再对小部件进行额外适配。
如果业务希望自行对小部件进行更完美，精细的适配，可以选择将全局缩放能力关闭，只需要在小部件对应的provider里，加上如下所示的配置即可
<receiver
    android:name=".service.normal.widget.NormalExampleWidgetProvider">
    <meta-data
        android:name="miuiAutoScale"
        android:value="false"/>
</receiver>
提示： 对于使用了GridView或者ListView的小部件，该方法不能使用，如遇到问题，请联系相关技术支持。
12、注意事项
12.1、圆角
小米Widget 虽然会在小米系统13上统一裁切圆角，但为了能够兼容旧版系统和非小米手机，开发者仍需为小米Widget添加圆角 ，圆角大小与设计规范保持一致。
12.3、刷新兼容
曝光刷新仅存在支持小米Widget的系统中(可通过“小米小部件系统能力说明--是否支持小米Widget”判断)，在不支持小米Widget 的系统以及非小米手机上，小米Widget刷新机制与原生Widget保持一致，各项目需要根据原生刷新机制以及业务需求进行适配。
12.4、Activity 进程
系统会对 Widget 进程进行优化，系统资源紧张时，Widget 进程容易被回收。为了保证 Activity 的正常显示，Activity所在进程不能是Widget 进程。
12.5、小部件名称
小米Widget必须配置小部件名称。开发者可以根据每个小部件的功能，为小部件撰写一个简洁的名字（2~8个汉字），帮助用户理解小部件的用途。在小部件中心里，小部件名称会展示为“应用名称·小部件名称”，为了展示体验更佳，小部件名称与应用名称不得相同。小部件名称对应代码AndroidManifest.xml中receiver的label。设置方法如下：
<receiver
    android:name="com.miui.widgetdemo.provider.ExampleWidgetProvider"
    android:label="@string/app_widget_example"
    android:process=":widgetProvider">
    ...
</receiver>    
12.5、深色模式
小米Widget 只能在xml中静态适配深色模式（通过配置drawable-night、values-night等资源文件适配），不支持在RemoteViews通过代码动态设置深色模式。
12.6、禁止随意修改小米Widget的receiver类名
Android系统会根据receiver类名标识小米Widget。一旦改名，用户在旧版添加的小部件升级后就会消失。因此小米Widget 一旦审核通过，禁止随意修改receiver类名。
12.7、小部件通过审核后，禁止随意移除小米Widget 标识
通过审核的小部件移除小米Widget 标识，会导致系统无法识别，造成一些严重的错误，因此小部件一旦审核通过，禁止随意移除小米Widget 标识。
四、小米小部件系统能力说明
1、调起小米Widget 商店里的详情页，添加应用的小米Widget
1.1、描述
启动 Widget 详情页，详情页会包含该 App 通过审核的Widget。用户可以左右滑动预览，并选择其中一个添加到桌面。
[上传文件]
1.2、调用方式
关键方法
appWidgetManager.requestPinAppWidget(myProvider, extras, null)
使用 extras 携带参数
addType: appWidgetDetail
widgetName: 小部件name,可选参数，用来指定打开详情页后定位到的组件。如果不填，默认定位到第一个。
widgetExtraData: 用于携带自定义参数，携带自定义参数类型只能是String，最多携带5个，超过5个所有携带的自定义参数都将被抛弃。
注意：该方法仅支持 Android 8.0 及以上系统。不支持小米Widget 的手机调用requestPinAppWidget 方法不会调起 Widget 商店里的详情页。
1.3、示例
public void startAppWidgetPage(Context context){
    AppWidgetManager appWidgetManager =  
    AppWidgetManager.getInstance(context);
    ComponentName myProvider = new ComponentName(this,  
    ExampleWidgetProvider.class);
if (appWidgetManager.isRequestPinAppWidgetSupported()) {
        Bundle extras = new Bundle();
        extras.putString("addType","appWidgetDetail");
// packageName 为应用真实包名
⁣       extras.putString("widgetName",
"packageName/com.miui.ExampleWidgetProvider");
        Bundle dataBundle = new Bundle();
        dataBundle.putString("dataKey1","data1xxx");
        dataBundle.putString("dataKey2","data2xxx");
        dataBundle.putString("dataKey3","data3xxx");
        dataBundle.putString("dataKey4","data4xxx");
        dataBundle.putString("dataKey5","data5xxx");
        extras.putBundle("widgetExtraData", dataBundle);
        appWidgetManager.requestPinAppWidget(myProvider, extras, null);
    }
}
// 获取自定义参数public class ExampleWidgetProvider extends AppWidgetProvider {
@Override
public void onAppWidgetOptionsChanged(Context context, AppWidgetManager
     appWidgetManager, int appWidgetId, Bundle newOptions) {
super.onAppWidgetOptionsChanged(context, appWidgetManager,
         appWidgetId, newOptions);
if(extras != null){
            Bundle dataBundle = extras.getBundle("widgetExtraData");
            dataBundle.getString("xxxkey");
        }   
    }    
}
2、设置小米Widget卡片状态
2.1、描述
小米Widget 也可以在智能助理（负一屏）进行展示。应用可以选择向系统发送卡片的优先级状态，智能助理（负一屏）会根据卡片状态进行动态的排序。
2.2、调用方式
key:    miuiWidgetEventCode     
类型:    String
描述： 事件code命名规则请参考code事件表
key:    miuiWidgetTimestamp
类型：   String
描述： 状态变化时的时间戳
appWidgetManager.updateAppWidgetOptions(widgetId, options);
appWidgetManager.updateAppWidget(widgetId, views);
2.3、code 事件表
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
| 事件code命名规则请参考下表，具体事件与code对应关系以双方沟通结果为准                                                                 |
| *新增或修改事件需与小米商务同学联系，上报未确认的事件code不会生效，恶意错报被系统识别后会降低推荐权重                                |
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
| 核心场景                   | 事件                           | 事件code                   | 备注                                      |
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
| 金融                       | 小部件内有股票/基金在交易      | opening                    |                                           |
| 股票证券                   |                                |                            |                                           |
|                            +--------------------------------+----------------------------+-------------------------------------------+
|                            | 小部件内无股票/基金在交易      | closing                    |                                           |
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
| 出行                       | 通勤时间                       | commuting                  |                                           |
|                            +--------------------------------+----------------------------+-------------------------------------------+
|                            | 非通勤时间                     | other                      |                                           |
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
| 直播/电台                  | 小部件内展示内容“直播中”       | live1                      | 不同的事件可用live1、live2...区分         |
|                            +--------------------------------+----------------------------+-------------------------------------------+
|                            | 小部件内展示内容“重播中”       | replay                     |                                           |
|                            +--------------------------------+----------------------------+-------------------------------------------+
|                            | 小部件内展示内容“未开播”       | other                      |                                           |
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
| 连接状态                   | 已连接                         | connected                  |                                           |
|                            +--------------------------------+----------------------------+-------------------------------------------+
|                            | 未连接                         | disconnect                 |                                           |
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
| 通知提醒                   | 小部件内展示有通知提醒提醒     | notice1                    | 不同的事件可用notice1、notice2...区分     |
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
| 进度状态                   | 如“打车进度”“下单进度”等       | progress1                  | 不同的事件可用progress1、progress2...区分 |
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
| 内容资讯                   | 如“今日热门”“话题榜”“热搜榜”等 | info1                      | 不同的事件可用info1、info2...区分         |
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
| 功能跳转                   | 小部件为纯工具类的功能跳转     | state1                     | 不同的事件可用state1、state2...区分       |
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
| 其他                       | 小部件的兜底展示方案或其他状态 | other                      |                                           |
+----------------------------+--------------------------------+----------------------------+-------------------------------------------+
2.4、示例
· 普通 Widget ：在 AppWidgetProvider 的 onUpdate 方法，调用 updateAppWidgetOptions 方法。
public class ExampleWidgetProvider extends AppWidgetProvider {
@Override
public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
// todo 准备待更新数据
        ....
// 更新数据
for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(),
            R.layout.widget_ui);
            Bundle options = new Bundle();
            options.putString("miuiWidgetEventCode", "notice1");
long currentTimeMillis = System.currentTimeMillis();
            options.putString("miuiWidgetTimestamp",
            String.valueOf(currentTimeMillis));
            appWidgetManager.updateAppWidgetOptions(widgetId, options);
            appWidgetManager.updateAppWidget(widgetId, views);
        }
    }
}
· 列表 Widget : notifyAppWidgetViewDataChanged 更新后调用 updateAppWidget 和updateAppWidgetOptions 方法
public class ExampleWidgetProvider extends AppWidgetProvider {
@Override
public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
for (int appWidgetId : appWidgetIds) {
// todo 准备待更新数据
            ...
            appWidgetManager.notifyAppWidgetViewDataChanged (mAppWidgetId,R.id.content);
// 更新数据后进行状态更新
            Bundle options = new Bundle();
            options.putString("miuiWidgetEventCode", "notice1");
long currentTimeMillis = System.currentTimeMillis();
            options.putString("miuiWidgetTimestamp",
            String.valueOf(currentTimeMillis));
            appWidgetManager.updateAppWidgetOptions(mAppWidgetId, options);
            RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(),             R.layout.widget_list_example);
            appWidgetManager.updateAppWidget(mAppWidgetId, remoteViews);
        }
    }
}
注意：updateAppWidgetOptions 需要在 updateAppWidget ()方法前，并且两者需要同一线程。
3、设置编辑页
3.1、描述
小米Widget 额外提供进入编辑页的入口。用户点击编辑按钮，可以进入编辑页进行相应的设置。
3.2、调用方法与参数
Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
String uriPath = "widgetdemo://com.miui.widgetdemo/widget/WidgetEditActivity";
options.putString("miuiEditUri", uriPath);
appWidgetManager.updateAppWidgetOptions(appWidgetId, options);
appWidgetManager.updateAppWidget(widgetId, views);
3.3、示例
public class ExampleWidgetProvider extends AppWidgetProvider {
@Override
public void onUpdate(Context context, AppWidgetManager
        appWidgetManager, int[] appWidgetIds) {
for (int appWidgetId : appWidgetIds) {          
            Bundle options =   
            appWidgetManager.getAppWidgetOptions(appWidgetId);
            String uriPath =            "widgetdemo://com.miui.widgetdemo/widget/WidgetEditActivity";
            options.putString("miuiEditUri", uriPath);
            appWidgetManager.updateAppWidgetOptions(appWidgetId, options);
            RemoteViews views =   
new RemoteViews(context.getPackageName(),
            R.layout.widget_ui);
            appWidgetManager.updateAppWidget(widgetId, views);
        }
        ....
    }
}// 通过 intent 可以获取点击的appWidgetIdpublic class WidgetEditActivity extends AppCompatActivity {
@Override
protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
    }
}
4、同功能小米Widget 聚合
4.1、描述
小米Widget 可以把不同尺寸相同功能的Widget 在详情页中聚合在一块显示，用户可以根据需求添加相应尺寸的Widget。
4.2、调用方式
在AndroidManifest.xml 文件中 AppWidgetProvider 对应的receiver 如果label 的名称相同的话将会被认为是同一功能的不同尺寸。
4.3、示例
<receiver
    android:name="com.miui.widgetdemo.provider.ExampleWidgetProvider1"
    android:label="@string/app_widget_group">
    ...
</receiver>
<receiver
    android:name="com.miui.widgetdemo.provider.ExampleWidgetProvider2"
    android:label="@string/app_widget_group">
   ...
</receiver>
5、点击小米Widget 跳转应用页面
5.1、描述
在 AppWidgetProvider 的onUpdate方法中，使用 RemoteViews 的 setOnClickPendingIntent 设置启动的 Intent 。使用 AppWidgetManager 的 updateAppWidget方法关联RemoteViews 和Widget。
5.2、参数
// viewId Widget 布局 id// PendingIntent Intent的封装// appWidgetId Widget 的 id，可在 onUpdate 方法参数中获取// appWidgetManager widget 的管理对象，可在 onUpdate 方法参数获取
remoteViews.setOnClickPendingIntent(int viewId, PendingIntent pendingIntent)
appWidgetManager.updateAppWidget(int appWidgetId, RemoteViews views)
5.3、示例
// step1: 构建跳转页面的 PendingIntent
Intent intent = new Intent(context, ExampleActivity.class);
PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);// step2: 生成 RemoteViews 关联 PendingIntent
RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.appwidget_provider_layout);
views.setOnClickPendingIntent(R.id.button, pendingIntent);// step3: 关联 widget 和 RemoteViews
appWidgetManager.updateAppWidget(appWidgetId, views);
6、判断小米Widget 是否已经添加
6.1、描述
使用系统 AppWidgetManager 的 getAppWidgetIds方法判断某个Widget是否已添加。
6.2、示例
ComponentName componentName = new ComponentName(getPackageName(), "com.miui.ExampleAppWidgetProvider");int[] appWidgetIds = AppWidgetManager.getInstance(getApplicationContext()).getAppWidgetIds(componentName);if(appWidgetIds.length > 0){
// 已添加
} else {
// 未添加
}
7、判断是否支持小米Widget
7.1、描述
开发者可以通过示例方式判断当前手机是否支持小米Widget。
7.2、示例
@WorkerThreadpublic boolean isMiuiWidgetSupported() {
    Uri uri =
    Uri.parse("content://com.miui.personalassistant.widget.external");
boolean isMiuiWidgetSupported = false;
try {
        Bundle bundle = getContentResolver().call(uri,
"isMiuiWidgetSupported", null, null);
if (bundle != null) {
            isMiuiWidgetSupported = bundle.getBoolean("isMiuiWidgetSupported");
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
return isMiuiWidgetSupported;
}
8、判断是否支持小米Widget 小部件详情页
8.1、描述
开发者可以通过示例方式判断当前手机是否支持小米Widget 详情页。为了提升用户体验，部分机型支持小米Widget（包含小米Widget 特性，例如曝光刷新等）, 但不支持调起小米Widget 详情页。这部分机型添加小部件的方式与旧版系统一致。
8.2、示例
@WorkerThreadpublic boolean isMiuiWidgetDetailPageSupported() {
    Uri uri =
    Uri.parse("content://com.miui.personalassistant.widget.external");
boolean isMiuiWidgetDetailPageSupported = false;
try {
        Bundle bundle = getContentResolver().call(uri,
"isMiuiWidgetDetailPageSupported", null, null);
if (bundle != null) {
            isMiuiWidgetDetailPageSupported = bundle.getBoolean("isMiuiWidgetDetailPageSupported");
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
return isMiuiWidgetDetailPageSupported;
}
9、小米Widget 与 Activity 切换动画
9.1、描述
小米系统为小米Widget 和 Activity 切换时增加了过渡动画。开发者可根据自身业务场景决定是否使用过渡动画。动画默认开启，如果不使用，设置"miuiWidgetTransitionAnimation"为false。
9.2、示例
<receiver android:name="com.miui.ExampleWidgetProvider" >
    <meta-data
    android:name="miuiWidget"
    android:value="true" />
// 关闭动画
    <meta-data
    android:name="miuiWidgetTransitionAnimation"
    android:value="false" />
    ...
</receiver>
10、App主动刷新小部件
10.1、描述
当应用在前台使用或在后台存活时，可调用AppWidgetManager. updateAppWidget()方法，主动刷新小部件。这样可以提高小部件内容的实时性和准确性。
10.2、示例
// 通过组件名更新public void updateWidget(Context context) {
// R.layout.demo_appwidget_normal_example 为小部件布局文件，这里仅示例
    RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
            R.layout.demo_appwidget_normal_example);
    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
// NormalExampleWidgetProvider 为小部件组件名字，这里仅示例
    ComponentName componentName = new ComponentName(context, NormalExampleWidgetProvider.class);
    appWidgetManager.updateAppWidget(componentName, remoteViews);
}// 通过widgetId更新public void updateWidgetByWidgetId(Context context, int widgetId) {
// R.layout.demo_appwidget_normal_example 为小部件布局文件，这里仅示例
    RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
            R.layout.demo_appwidget_normal_example);
    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
    appWidgetManager.updateAppWidget(widgetId, remoteViews);
}
11、Push透传刷新服务
11.1、 描述
当小米Widget状态发生改变且Widget不可见、主应用未启动时，开发者可调用小部件服务端提供的更新API，经由MI PUSH发送透传消息给负一屏/桌面客户端，由负一屏/桌面拉起Widget独立进程（不唤醒应用主进程），以实现小米Widget实时刷新的目的。
流程图：
[上传文件]
11.3、接口规范
域名: https://developer.assistant.miui.com
路径: /openapi/widget/{cpCode}/refresh
     cpCode值由小部件开发人员提供
参数：需包含Header以及Body
+----------------+--------------------------------------------------------+----------+--------+--------------------------------------+
| Header参数表                                                                                                                       |
+----------------+--------------------------------------------------------+----------+--------+--------------------------------------+
| 头域名称       | 描述                                                   | 是否必选 | 类型   | 取值范围                             |
+----------------+--------------------------------------------------------+----------+--------+--------------------------------------+
| Content-Type   | 固定值，填application/json                             | 是       | String | application/json                     |
+----------------+--------------------------------------------------------+----------+--------+--------------------------------------+
| app-id         | 在小米开放平台注册的程序编号                           | 是       | String |                                      |
+----------------+--------------------------------------------------------+----------+--------+--------------------------------------+
| access-token   | 应用级token(校验请求权限，通过帐号服务获取)            | 是       | String | 最大长度:259                         |
+----------------+--------------------------------------------------------+----------+--------+--------------------------------------+
| sign           | 签名（验证请求完整性，下方有签名生成方法）             | 是       | String |                                      |
+----------------+--------------------------------------------------------+----------+--------+--------------------------------------+
| timestamp      | 当前时间戳（防止请求重放，会根据该字段判断请求有效期） | 是       | long   | 13位 毫秒时间戳                      |
+----------------+--------------------------------------------------------+----------+--------+--------------------------------------+
| trace-id       | 请求的唯一标识（用来定位每次请求）                     | 是       | String | 只能包含数字和大小写字母，最大长度64 |
+----------------+--------------------------------------------------------+----------+--------+--------------------------------------+
+--------------------+------------------------------+------+--------+
| Body参数表                                                        |
+--------------------+------------------------------+------+--------+
| 字段名             | 描述                         | 必须 | 类型   |
+--------------------+------------------------------+------+--------+
| oaid               | 设备oaid                     | 是   | String |
+--------------------+------------------------------+------+--------+
| widgetId           | 设备 widgetId (安卓生成的id) | 是   | String |
+--------------------+------------------------------+------+--------+
| widgetProviderName | 小部件providerName           | 是   | String |
+--------------------+------------------------------+------+--------+
| extra              | 额外透传到小部件内容         | 否   | String |
+--------------------+------------------------------+------+--------+
注意：
- 获取access-token：access-token的获取方式请联系相关技术支持。
- 处理签名
签名生成:
假设: appId = "111111111" , 当前时间戳是 1606206667013 ，秘钥是 "testSecret"
    step 1: 先将body进行md5，再转化为16进制小写字符串
公式：md5Body= Lowercase(hexEncode(md5(body)))
  step 2: 将appId、毫秒时间戳和body的md5Body值，按照参数名称进行字典排序，用‘&’连接获得strToDigest
如：strToDigest="appId=111111111&body=md5Body×tamp=1606206667013"
    step 3: 将获得到的摘要字符串（strToDigest）进行HMAC_SHA_256，再转化为16进制小写字符串得到签名
公式：Lowercase(hexEncode(HMAC_SHA_256("testSecret", strToDigest)))
// 签名java demo: // demo引用了apache的工具类进行处理，若不想引用可以参考其逻辑自行实现
<!-- https://mvnrepository.com/artifact/commons-codec/commons-codec -->
<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
    <version>1.14</version>
</dependency>
@Slf4jpublic class SignatureUtils {
/**
     * 生成签名
     *
     * @param appId     应用id
     * @param secret    秘钥
     * @param timestamp 请求的时间戳
     * @param body      请求的body内容
     * @return 签名信息
     */
public static String generateSignature(String appId, String secret, String timestamp, String body) {
        String sign = null;
try {
            String md5Body = DigestUtils.md5Hex(body);
            Map<String, String> paramMap = new TreeMap<>();
            paramMap.put("appId", appId);
            paramMap.put("timestamp", timestamp);
            paramMap.put("body", md5Body);
            String strToDigest = paramMap.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
            sign = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secret).hmacHex(strToDigest);
        } catch (Exception e) {
            log.error("generate signature exception !", e);
        }
return sign;
    }
/**
     * 验证签名
     *
     * @param appId     应用id
     * @param secret    秘钥
     * @param timestamp 请求的时间戳
     * @param body      请求的body内容
     * @param sign      请求的签名
     * @return 签名信息
     */
public static boolean verifySignature(String appId, String secret, String timestamp, String body, String sign) {
return sign.equals(generateSignature(appId, secret, timestamp, body));
    }
}
· 返回状态码定义
开发者小米Widget可从刷新广播的Intent中得到extra信息。
11.3、示例
// Curl示例：
curl --location --request POST 'http://developer.assistant.miui.com/openapi/widget/testCp/refresh' \
    --header 'app-id: 111111111' \
    --header 'access-token: A1_oKs19iiWArgij6qFYaEAooAMqG3bhXUgS0MZQf63KQTgWju-oj9YuccqR1EhbugrqnFmooNr6mKdkfEdKN740fUYpxk0o9ZHE5QpFvR1fhaoJ7xELYD1byNnYZb-tB-y5DPXRLIp8ikod5lUZGnayuLVPePa7uB1LlVzw-qPS-U' \
    --header 'sign: 664d528f398af20f23b6e4ec43d4e9662476ee8fc9c5cee42dd897b1af0152e7' \
    --header 'timestamp: 1636341480000' \
    --header 'trace-id: 123123' \
    --header 'Content-Type: application/json' \
    --data-raw '{
        "oaid": "f29eb7d12222fb6b",
        "widgetId": "666",
        "widgetProviderName": "com.miui.test",
        "extra": "{\"test\":123}"
    }'
三、联系我们
产品技术：miui-widget@xiaomi.com
[]内容反馈
[]上一篇:Xiaomi HyperOS小部件设计规范
下一篇:Widget适配建议及示例[]
文档内容是否有帮助？
[]有帮助
[]无帮助
[]
[logo]
开发
应用开发
快应用开发
小游戏开发
设备开发
分发
应用分发
游戏分发
快应用分发
小游戏分发
全搜
负一屏
推广与变现
营销平台
游戏联运
广告联盟
应用联运
支持
公告
文档中心
联系我们
友情链接
小米官网
小米商城
小米IoT
小爱同学
关注我们
[]
京公网安备11010802020134号
|
京ICP备10046444号
小米科技有限责任公司 Copyright© mi.com
|
隐私政策
|
发布协议
|
反馈邮箱：developer@xiaomi.com
