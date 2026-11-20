import AppKit

final class LayoutEditorAppDelegate: NSObject, NSApplicationDelegate {
    private var process: Process?
    private var logHandle: FileHandle?
    private var recentPaths: [URL] = []
    private var recentMenu: NSMenu?

    private var projectRoot: URL {
        Bundle.main.bundleURL.deletingLastPathComponent().deletingLastPathComponent()
    }

    private var supportDirectory: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("JS Dashboard Layout Studio", isDirectory: true)
    }

    private var recentStore: URL {
        supportDirectory.appendingPathComponent("recent-directories.json")
    }

    private var legacyRecentStore: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("JS Dashboard Preview/recent-directories.json")
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        loadRecentPaths()
        addRecent(projectRoot)
        buildMenu()
        launchStudio(at: projectRoot)
    }

    private func buildMenu() {
        let mainMenu = NSMenu()
        let appItem = NSMenuItem()
        let appMenu = NSMenu()
        let quitItem = NSMenuItem(title: "Quit 레이아웃 스튜디오", action: #selector(quit), keyEquivalent: "q")
        quitItem.target = self
        appMenu.addItem(quitItem)
        appItem.submenu = appMenu
        mainMenu.addItem(appItem)

        let fileItem = NSMenuItem()
        let fileMenu = NSMenu(title: "File")
        let openItem = NSMenuItem(title: "Open Project…", action: #selector(openFolder), keyEquivalent: "o")
        openItem.target = self
        fileMenu.addItem(openItem)
        let newItem = NSMenuItem(title: "New Repository…", action: #selector(createRepository), keyEquivalent: "n")
        newItem.target = self
        fileMenu.addItem(newItem)
        fileMenu.addItem(NSMenuItem.separator())
        let recentItem = NSMenuItem(title: "Open Recent", action: nil, keyEquivalent: "")
        let menu = NSMenu(title: "Open Recent")
        recentItem.submenu = menu
        recentMenu = menu
        fileMenu.addItem(recentItem)
        fileItem.submenu = fileMenu
        mainMenu.addItem(fileItem)
        NSApp.mainMenu = mainMenu
        rebuildRecentMenu()
    }

    @objc private func openFolder() {
        let panel = NSOpenPanel()
        panel.title = "Open Layout Studio Project"
        panel.message = "Choose a project folder that contains tools/layout_editor.py."
        panel.canChooseFiles = false
        panel.canChooseDirectories = true
        panel.allowsMultipleSelection = false
        panel.begin { [weak self] response in
            guard response == .OK, let url = panel.url else { return }
            self?.addRecent(url)
            self?.launchStudio(at: url)
        }
    }

    @objc private func createRepository() {
        let panel = NSOpenPanel()
        panel.title = "Choose Where to Create the Repository"
        panel.message = "Choose the parent directory for the new repository folder."
        panel.canChooseFiles = false
        panel.canChooseDirectories = true
        panel.allowsMultipleSelection = false
        panel.begin { [weak self] response in
            guard response == .OK, let parent = panel.url else { return }
            self?.askRepositoryName(in: parent)
        }
    }

    private func askRepositoryName(in parent: URL) {
        let alert = NSAlert()
        alert.messageText = "Create a Git repository"
        alert.informativeText = "A new folder will be created in:\n\(parent.path)"
        alert.addButton(withTitle: "Create Repository")
        alert.addButton(withTitle: "Cancel")
        let nameField = NSTextField(string: "NewRepository")
        nameField.frame = NSRect(x: 0, y: 0, width: 280, height: 24)
        alert.accessoryView = nameField
        guard alert.runModal() == .alertFirstButtonReturn else { return }

        let name = nameField.stringValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, name != ".", name != "..", !name.contains("/") else {
            showError("Enter a valid folder name.")
            return
        }
        let destination = parent.appendingPathComponent(name, isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: false)
            let git = Process()
            git.executableURL = URL(fileURLWithPath: "/usr/bin/git")
            git.arguments = ["init", destination.path]
            let output = Pipe()
            git.standardOutput = output
            git.standardError = output
            try git.run()
            git.waitUntilExit()
            guard git.terminationStatus == 0 else {
                showError("Git could not initialize the new folder.\n\(read(output))")
                return
            }
            addRecent(destination)
            launchStudio(at: destination)
        } catch {
            showError("Could not create the repository folder.\n\(error.localizedDescription)")
        }
    }

    private func read(_ pipe: Pipe) -> String {
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        return String(data: data, encoding: .utf8) ?? ""
    }

    @objc private func openRecent(_ sender: NSMenuItem) {
        guard let path = sender.representedObject as? String else { return }
        let url = URL(fileURLWithPath: path, isDirectory: true)
        addRecent(url)
        launchStudio(at: url)
    }

    private func launchStudio(at directory: URL) {
        let script = directory.appendingPathComponent("tools/layout_editor.py")
        guard FileManager.default.fileExists(atPath: script.path) else {
            showError("\(directory.path) does not contain tools/layout_editor.py and cannot be opened as a Layout Studio project.")
            return
        }
        guard let python = findPython() else {
            showError("Python 3.9 이상을 찾지 못했습니다.")
            return
        }

        let previousProcess = process
        process = nil
        previousProcess?.terminate()
        do {
            try FileManager.default.createDirectory(at: supportDirectory, withIntermediateDirectories: true)
            let logURL = FileManager.default.homeDirectoryForCurrentUser
                .appendingPathComponent("Library/Logs/JS Dashboard Layout Studio/launcher.log")
            try FileManager.default.createDirectory(at: logURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            try Data("Starting Layout Studio\nProject: \(directory.path)\n".utf8).write(to: logURL, options: .atomic)
            logHandle = try FileHandle(forWritingTo: logURL)

            let child = Process()
            child.executableURL = python
            child.arguments = [script.path]
            child.currentDirectoryURL = directory
            child.standardOutput = logHandle
            child.standardError = logHandle
            child.terminationHandler = { [weak self] process in
                DispatchQueue.main.async {
                    guard let self, let current = self.process, current === process else { return }
                    self.process = nil
                    guard process.terminationStatus != 0 else { return }
                    self.showError("레이아웃 스튜디오가 오류 코드 \(process.terminationStatus)로 종료되었습니다. 원문 오류 로그: \(logURL.path)")
                }
            }
            try child.run()
            process = child
            NSApp.activate(ignoringOtherApps: true)
        } catch {
            showError("레이아웃 스튜디오를 실행하지 못했습니다: \(error.localizedDescription)")
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

    private func addRecent(_ url: URL) {
        let normalized = url.standardizedFileURL
        recentPaths.removeAll { $0.standardizedFileURL == normalized }
        recentPaths.insert(normalized, at: 0)
        if recentPaths.count > 20 { recentPaths = Array(recentPaths.prefix(20)) }
        rebuildRecentMenu()
        saveRecentPaths()
    }

    private func loadRecentPaths() {
        let source = FileManager.default.fileExists(atPath: recentStore.path) ? recentStore : legacyRecentStore
        guard let data = try? Data(contentsOf: source),
              let paths = try? JSONDecoder().decode([String].self, from: data) else { return }
        recentPaths = paths.map { URL(fileURLWithPath: $0, isDirectory: true) }
            .filter { FileManager.default.fileExists(atPath: $0.path) }
    }

    private func saveRecentPaths() {
        do {
            try FileManager.default.createDirectory(at: supportDirectory, withIntermediateDirectories: true)
            let data = try JSONEncoder().encode(recentPaths.map(\.path))
            try data.write(to: recentStore, options: .atomic)
        } catch {
            showError("Could not save recent projects: \(error.localizedDescription)")
        }
    }

    private func rebuildRecentMenu() {
        guard let recentMenu else { return }
        recentMenu.removeAllItems()
        if recentPaths.isEmpty {
            let empty = NSMenuItem(title: "No Recent Projects", action: nil, keyEquivalent: "")
            empty.isEnabled = false
            recentMenu.addItem(empty)
            return
        }
        for url in recentPaths {
            let item = NSMenuItem(title: url.path, action: #selector(openRecent(_:)), keyEquivalent: "")
            item.target = self
            item.representedObject = url.path
            recentMenu.addItem(item)
        }
    }

    @objc private func quit() {
        let child = process
        process = nil
        child?.terminate()
        NSApp.terminate(nil)
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }

    private func showError(_ message: String) {
        let alert = NSAlert()
        alert.messageText = "레이아웃 스튜디오"
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
