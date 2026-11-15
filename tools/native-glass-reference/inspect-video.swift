import Foundation
import AVFoundation
import AppKit

// Inspect decoded frame timestamps and extract actual decoded frames. No frame
// interpolation, requested frame rate, or animation speed change is involved.
guard CommandLine.arguments.count >= 3 else {
    fputs("Usage: swift inspect-video.swift VIDEO OUTPUT_DIRECTORY [VIDEO_SECONDS ...]\n", stderr)
    exit(1)
}
let input = URL(fileURLWithPath: CommandLine.arguments[1])
let output = URL(fileURLWithPath: CommandLine.arguments[2], isDirectory: true)
try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
let asset = AVURLAsset(url: input)
guard let track = asset.tracks(withMediaType: .video).first else {
    fputs("Input has no video track\n", stderr)
    exit(1)
}
let reader = try AVAssetReader(asset: asset)
let stream = AVAssetReaderTrackOutput(track: track, outputSettings: [
    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
])
guard reader.canAdd(stream) else {
    fputs("Cannot decode video track\n", stderr)
    exit(1)
}
reader.add(stream)
guard reader.startReading() else { throw reader.error ?? NSError(domain: "VideoInspection", code: 1) }
var timestamps: [Double] = []
var invalidTimestamps = 0
while let sample = stream.copyNextSampleBuffer() {
    let timestamp = CMSampleBufferGetPresentationTimeStamp(sample).seconds
    if timestamp.isFinite { timestamps.append(timestamp) } else { invalidTimestamps += 1 }
}
timestamps.sort()
guard reader.status == .completed else { throw reader.error ?? NSError(domain: "VideoInspection", code: 2) }
guard !timestamps.isEmpty else {
    fputs("Video contains no decoded frames with finite timestamps\n", stderr)
    exit(1)
}
let intervals = zip(timestamps.dropFirst(), timestamps).map { ($0 - $1) * 1_000 }.sorted()
func percentile(_ fraction: Double) -> Double {
    intervals.isEmpty ? 0 : intervals[Int(Double(intervals.count - 1) * fraction)]
}
let duration = asset.duration.seconds
let metadata: [String: Any] = [
    "video": input.path, "duration_s": duration.isFinite ? duration as Any : NSNull(),
    "decoded_frames": timestamps.count, "track_nominal_fps": track.nominalFrameRate,
    "invalid_timestamp_samples": invalidTimestamps,
    "duplicate_timestamp_samples": timestamps.count - Set(timestamps).count,
    "reader_status": reader.status.rawValue, "reader_error": reader.error.map { String(describing: $0) } ?? "",
    "decoded_pts_interval_ms_min": intervals.first ?? 0,
    "decoded_pts_interval_ms_p50": percentile(0.5), "decoded_pts_interval_ms_p95": percentile(0.95),
    "decoded_pts_interval_ms_max": intervals.last ?? 0,
    "average_decoded_frames_per_video_second": duration.isFinite && duration > 0 ? Double(timestamps.count) / duration as Any : NSNull(),
    "width_px": track.naturalSize.width, "height_px": track.naturalSize.height,
    "creation_date": asset.creationDate?.dateValue.map { ISO8601DateFormatter().string(from: $0) } ?? "",
    "limitation": "Variable-rate encoded video samples describe this capture, not physical display refresh or millisecond presentation."
]
try JSONSerialization.data(withJSONObject: metadata, options: [.prettyPrinted, .sortedKeys])
    .write(to: output.appendingPathComponent("video-metadata.json"))
try ("frame,pts_s\n" + timestamps.enumerated().map { "\($0.offset),\($0.element)" }.joined(separator: "\n"))
    .write(to: output.appendingPathComponent("video-pts.csv"), atomically: true, encoding: .utf8)
print(String(data: try JSONSerialization.data(withJSONObject: metadata, options: [.sortedKeys]), encoding: .utf8)!)

let requests = CommandLine.arguments.dropFirst(3).compactMap(Double.init)
guard !requests.isEmpty else { exit(0) }
let generator = AVAssetImageGenerator(asset: asset)
generator.appliesPreferredTrackTransform = true
generator.requestedTimeToleranceBefore = .zero
generator.requestedTimeToleranceAfter = .zero
let columns = 6, tileWidth = 201, tileHeight = 184
let sheet = NSImage(size: NSSize(width: columns * tileWidth,
                               height: ((requests.count + columns - 1) / columns) * tileHeight))
sheet.lockFocus()
NSColor.white.setFill()
NSRect(origin: .zero, size: sheet.size).fill()
var exported: [[String: Any]] = []
for (index, requested) in requests.enumerated() {
    let nearest = timestamps.min { abs($0 - requested) < abs($1 - requested) }!
    var actual = CMTime.zero
    let image = try generator.copyCGImage(at: CMTime(seconds: nearest, preferredTimescale: 600_000), actualTime: &actual)
    let filename = String(format: "frame-%03d-%.6f.png", index, actual.seconds)
    let png = NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:])!
    try png.write(to: output.appendingPathComponent(filename))
    // Contact-sheet crop is specific to the 402 pt language reference layout.
    // Every full-resolution frame is also exported; use it for other layouts.
    let scale = CGFloat(image.width) / 402
    let crop = image.cropping(to: CGRect(x: 0, y: 180 * scale,
                                      width: CGFloat(image.width), height: 320 * scale)) ?? image
    let x = (index % columns) * tileWidth
    let y = Int(sheet.size.height) - ((index / columns) + 1) * tileHeight
    NSImage(cgImage: crop, size: .zero).draw(in: NSRect(x: x, y: y + 20, width: tileWidth, height: 160))
    let label = String(format: "#%d video %.6f s", index, actual.seconds)
    label.draw(at: NSPoint(x: x + 3, y: y + 3), withAttributes: [.font: NSFont.monospacedSystemFont(ofSize: 10, weight: .regular), .foregroundColor: NSColor.black])
    exported.append(["requested_video_s": requested, "actual_video_s": actual.seconds,
                     "decoded_frame": timestamps.firstIndex(of: nearest)!, "png": filename])
}
sheet.unlockFocus()
let bitmap = NSBitmapImageRep(data: sheet.tiffRepresentation!)!
try bitmap.representation(using: .png, properties: [:])!.write(to: output.appendingPathComponent("contact-sheet.png"))
try JSONSerialization.data(withJSONObject: exported, options: [.prettyPrinted, .sortedKeys])
    .write(to: output.appendingPathComponent("exported-frames.json"))
