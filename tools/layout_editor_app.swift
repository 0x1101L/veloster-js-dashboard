import AppKit

final class LayoutEditorAppDelegate: NSObject, NSApplicationDelegate {
    private var process: Process?
    private var logHandle: FileHandle?

    private var projectRoot: URL {
        Bundle.main.bundleURL.deletingLastPathComponent().deletingLastPathComponent()
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        let script = projectRoot.appendingPathComponent("tools/layout_editor.py")
        guard FileManager.default.fileExists(atPath: script.path) else {
            showError("에디터 소스를 찾을 수 없습니다. 앱을 저장소의 dist 폴더 안에서 실행하세요.")
            NSApp.terminate(nil)
            return
        }

        let logDirectory = FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent("Library/Logs/JS Dashboard Layout Editor", isDirectory: true)
        let logURL = logDirectory.appendingPathComponent("launcher.log")
        do {
            try FileManager.default.createDirectory(at: logDirectory, withIntermediateDirectories: true)
            FileManager.default.createFile(atPath: logURL.path, contents: Data())
            logHandle = try FileHandle(forWritingTo: logURL)
            try logHandle?.write(contentsOf: Data("Starting Dashboard Layout Editor\nProject: \(projectRoot.path)\n".utf8))
        } catch {
            showError("진단 로그를 만들지 못했습니다: \(error.localizedDescription)")
            NSApp.terminate(nil)
            return
        }

        guard let python = findPython() else {
            showError("Python 3.9 이상을 찾지 못했습니다. 로그: \(logURL.path)")
            NSApp.terminate(nil)
            return
        }

        let child = Process()
        child.executableURL = python
        child.arguments = [script.path]
        child.currentDirectoryURL = projectRoot
        child.standardOutput = logHandle
        child.standardError = logHandle
        child.terminationHandler = { [weak self] process in
            guard process.terminationStatus != 0 else { return }
            DispatchQueue.main.async {
                self?.showError("에디터가 오류 코드 \(process.terminationStatus)로 종료되었습니다. 원문 오류 로그: \(logURL.path)")
                NSApp.terminate(nil)
            }
        }

        do {
            try child.run()
            process = child
            NSApp.activate(ignoringOtherApps: true)
        } catch {
            showError("에디터를 실행하지 못했습니다: \(error.localizedDescription)")
            NSApp.terminate(nil)
        }
    }

    private func findPython() -> URL? {
        let environment = ProcessInfo.processInfo.environment
        var candidates = [
            "/Library/Frameworks/Python.framework/Versions/3.11/bin/python3",
            "/opt/homebrew/bin/python3",
            "/usr/local/bin/python3",
            "/usr/bin/python3"
        ]
        candidates += (environment["PATH"] ?? "").split(separator: ":").map { "\($0)/python3" }
        var seen = Set<String>()
        for path in candidates where seen.insert(path).inserted && FileManager.default.isExecutableFile(atPath: path) {
            let probe = Process()
            probe.executableURL = URL(fileURLWithPath: path)
            probe.arguments = ["-c", "import sys; assert sys.version_info >= (3, 9)"]
            probe.standardOutput = FileHandle.nullDevice
            probe.standardError = FileHandle.nullDevice
            do {
                try probe.run()
                probe.waitUntilExit()
                if probe.terminationStatus == 0 { return URL(fileURLWithPath: path) }
            } catch { continue }
        }
        return nil
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }

    private func showError(_ message: String) {
        let alert = NSAlert()
        alert.messageText = "Dashboard Layout Editor"
        alert.informativeText = message
        alert.alertStyle = .critical
        alert.runModal()
    }
}

@main
enum LayoutEditorMain {
    static func main() {
        let app = NSApplication.shared
        app.setActivationPolicy(.regular)
        let delegate = LayoutEditorAppDelegate()
        app.delegate = delegate
        app.run()
    }
}
