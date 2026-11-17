import AppKit

final class PreviewAppDelegate: NSObject, NSApplicationDelegate, NSTableViewDataSource, NSTableViewDelegate {
    private var managerWindow: NSWindow!
    private var table: NSTableView!
    private var statusLabel: NSTextField!
    private var recentPaths: [URL] = []
    private var renderer: Process?
    private var recentMenu: NSMenu?

    private var projectRoot: URL {
        Bundle.main.bundleURL.deletingLastPathComponent().deletingLastPathComponent()
    }

    private var recentStore: URL {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return support.appendingPathComponent("JS Dashboard Preview", isDirectory: true)
            .appendingPathComponent("recent-directories.json")
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        loadRecentPaths()
        if recentPaths.isEmpty { addRecent(projectRoot) }
        buildMenu()
        buildManagerWindow()
        showManager()
    }

    private func buildMenu() {
        let mainMenu = NSMenu()
        let appItem = NSMenuItem()
        let appMenu = NSMenu()
        let quitItem = NSMenuItem(title: "Quit JS Dashboard Preview", action: #selector(quit), keyEquivalent: "q")
        quitItem.target = self
        appMenu.addItem(quitItem)
        appItem.submenu = appMenu
        mainMenu.addItem(appItem)

        let fileItem = NSMenuItem()
        let fileMenu = NSMenu(title: "File")
        let openItem = NSMenuItem(title: "Open…", action: #selector(openFolder), keyEquivalent: "o")
        openItem.target = self
        fileMenu.addItem(openItem)
        let newRepositoryItem = NSMenuItem(title: "New Repository…", action: #selector(createRepository), keyEquivalent: "n")
        newRepositoryItem.target = self
        fileMenu.addItem(newRepositoryItem)
        fileMenu.addItem(NSMenuItem.separator())
        let recentItem = NSMenuItem(title: "Open Recent", action: nil, keyEquivalent: "")
        let menu = NSMenu(title: "Open Recent")
        recentItem.submenu = menu
        recentMenu = menu
        fileMenu.addItem(recentItem)
        fileItem.submenu = fileMenu
        mainMenu.addItem(fileItem)

        let windowItem = NSMenuItem()
        let windowMenu = NSMenu(title: "Window")
        let showItem = NSMenuItem(title: "Show Project Window", action: #selector(showManager), keyEquivalent: "0")
        showItem.target = self
        windowMenu.addItem(showItem)
        windowItem.submenu = windowMenu
        mainMenu.addItem(windowItem)
        NSApp.mainMenu = mainMenu
        rebuildRecentMenu()
    }

    private func buildManagerWindow() {
        managerWindow = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 760, height: 500),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        managerWindow.title = "JS Dashboard Preview · Projects"
        managerWindow.center()

        let content = NSView()
        managerWindow.contentView = content
        let heading = NSTextField(labelWithString: "Open a dashboard project")
        heading.font = .boldSystemFont(ofSize: 19)
        let subtitle = NSTextField(labelWithString: "Choose a recent folder, open another directory, or create a Git repository.")
        subtitle.textColor = .secondaryLabelColor

        table = NSTableView()
        let column = NSTableColumn(identifier: NSUserInterfaceItemIdentifier("directory"))
        column.title = "Recent directories"
        column.width = 700
        table.addTableColumn(column)
        table.headerView = nil
        table.rowHeight = 30
        table.dataSource = self
        table.delegate = self
        table.target = self
        table.doubleAction = #selector(openSelectedRecent)

        let scroll = NSScrollView()
        scroll.hasVerticalScroller = true
        scroll.borderType = .bezelBorder
        scroll.documentView = table

        let openButton = NSButton(title: "Open Directory…", target: self, action: #selector(openFolder))
        let newButton = NSButton(title: "New Repository…", target: self, action: #selector(createRepository))
        let openRecentButton = NSButton(title: "Open Selected", target: self, action: #selector(openSelectedRecent))
        let buttons = NSStackView(views: [openButton, newButton, openRecentButton])
        buttons.orientation = .horizontal
        buttons.spacing = 10
        buttons.alignment = .centerY
        statusLabel = NSTextField(labelWithString: "Recent paths are stored in ~/Library/Application Support/JS Dashboard Preview.")
        statusLabel.textColor = .secondaryLabelColor
        statusLabel.lineBreakMode = .byTruncatingMiddle

        [heading, subtitle, scroll, buttons, statusLabel].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            content.addSubview($0)
        }
        NSLayoutConstraint.activate([
            heading.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 22),
            heading.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -22),
            heading.topAnchor.constraint(equalTo: content.topAnchor, constant: 20),
            subtitle.leadingAnchor.constraint(equalTo: heading.leadingAnchor),
            subtitle.trailingAnchor.constraint(equalTo: heading.trailingAnchor),
            subtitle.topAnchor.constraint(equalTo: heading.bottomAnchor, constant: 5),
            scroll.leadingAnchor.constraint(equalTo: heading.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: heading.trailingAnchor),
            scroll.topAnchor.constraint(equalTo: subtitle.bottomAnchor, constant: 16),
            scroll.bottomAnchor.constraint(equalTo: buttons.topAnchor, constant: -16),
            scroll.heightAnchor.constraint(greaterThanOrEqualToConstant: 250),
            buttons.leadingAnchor.constraint(equalTo: heading.leadingAnchor),
            buttons.bottomAnchor.constraint(equalTo: statusLabel.topAnchor, constant: -12),
            statusLabel.leadingAnchor.constraint(equalTo: heading.leadingAnchor),
            statusLabel.trailingAnchor.constraint(equalTo: heading.trailingAnchor),
            statusLabel.bottomAnchor.constraint(equalTo: content.bottomAnchor, constant: -14),
        ])
    }

    @objc private func showManager() {
        managerWindow.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    @objc private func openFolder() {
        let panel = NSOpenPanel()
        panel.title = "Open Directory"
        panel.message = "Choose a project directory to add to recent folders."
        panel.canChooseFiles = false
        panel.canChooseDirectories = true
        panel.allowsMultipleSelection = false
        panel.begin { [weak self] response in
            guard response == .OK, let url = panel.url else { return }
            self?.addRecent(url)
            self?.openDirectory(url)
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
            statusLabel.stringValue = "Created Git repository: \(destination.path)"
            showRendererUnavailable(for: destination)
        } catch {
            showError("Could not create the repository folder.\n\(error.localizedDescription)")
        }
    }

    private func read(_ pipe: Pipe) -> String {
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        return String(data: data, encoding: .utf8) ?? ""
    }

    @objc private func openSelectedRecent() {
        let row = table.selectedRow
        guard recentPaths.indices.contains(row) else { return }
        openDirectory(recentPaths[row])
    }

    @objc private func openRecentMenu(_ sender: NSMenuItem) {
        guard let path = sender.representedObject as? String else { return }
        openDirectory(URL(fileURLWithPath: path, isDirectory: true))
    }

    private func openDirectory(_ url: URL) {
        addRecent(url)
        renderer?.terminate()
        renderer = nil
        let previewScript = url.appendingPathComponent("tools/js_ui_preview.py")
        guard FileManager.default.fileExists(atPath: previewScript.path) else {
            statusLabel.stringValue = "Saved in recent directories. No Dashboard renderer was found in \(url.path)"
            showRendererUnavailable(for: url)
            return
        }

        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/zsh")
        process.arguments = ["-lc", "python3 tools/js_ui_preview.py"]
        process.currentDirectoryURL = url
        process.terminationHandler = { [weak self] _ in
            DispatchQueue.main.async {
                self?.renderer = nil
                self?.statusLabel.stringValue = "Preview closed. Recent directories are available here."
            }
        }
        do {
            try process.run()
            renderer = process
            statusLabel.stringValue = "Opened Dashboard preview from \(url.path)"
        } catch {
            showError("Could not launch the preview.\n\(error.localizedDescription)")
        }
    }

    private func showRendererUnavailable(for url: URL) {
        let alert = NSAlert()
        alert.messageText = "Directory saved"
        alert.informativeText = "\(url.path) is in Open Recent. To display a Dashboard preview, the directory must contain tools/js_ui_preview.py and its renderer sources."
        alert.alertStyle = .informational
        alert.runModal()
    }

    private func showError(_ message: String) {
        let alert = NSAlert()
        alert.messageText = "JS Dashboard Preview"
        alert.informativeText = message
        alert.alertStyle = .warning
        alert.runModal()
    }

    private func addRecent(_ url: URL) {
        let normalized = url.standardizedFileURL
        recentPaths.removeAll { $0.standardizedFileURL == normalized }
        recentPaths.insert(normalized, at: 0)
        if recentPaths.count > 20 { recentPaths = Array(recentPaths.prefix(20)) }
        table?.reloadData()
        rebuildRecentMenu()
        saveRecentPaths()
    }

    private func loadRecentPaths() {
        guard let data = try? Data(contentsOf: recentStore),
              let paths = try? JSONDecoder().decode([String].self, from: data) else { return }
        recentPaths = paths.map { URL(fileURLWithPath: $0, isDirectory: true) }
            .filter { FileManager.default.fileExists(atPath: $0.path) }
    }

    private func saveRecentPaths() {
        do {
            try FileManager.default.createDirectory(
                at: recentStore.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            let data = try JSONEncoder().encode(recentPaths.map(\.path))
            try data.write(to: recentStore, options: .atomic)
        } catch {
            statusLabel?.stringValue = "Could not save recent directories: \(error.localizedDescription)"
        }
    }

    private func rebuildRecentMenu() {
        guard let recentMenu else { return }
        recentMenu.removeAllItems()
        if recentPaths.isEmpty {
            let empty = NSMenuItem(title: "No Recent Directories", action: nil, keyEquivalent: "")
            empty.isEnabled = false
            recentMenu.addItem(empty)
            return
        }
        for url in recentPaths {
            let item = NSMenuItem(title: url.path, action: #selector(openRecentMenu(_:)), keyEquivalent: "")
            item.target = self
            item.representedObject = url.path
            recentMenu.addItem(item)
        }
    }

    func numberOfRows(in tableView: NSTableView) -> Int { recentPaths.count }

    func tableView(_ tableView: NSTableView, viewFor tableColumn: NSTableColumn?, row: Int) -> NSView? {
        guard recentPaths.indices.contains(row) else { return nil }
        let label = NSTextField(labelWithString: recentPaths[row].path)
        label.lineBreakMode = .byTruncatingMiddle
        label.toolTip = recentPaths[row].path
        return label
    }

    @objc private func quit() {
        renderer?.terminate()
        NSApp.terminate(nil)
    }

    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        renderer?.terminate()
        return .terminateNow
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }
}

@main
enum PreviewMain {
    static func main() {
        let app = NSApplication.shared
        app.setActivationPolicy(.regular)
        let delegate = PreviewAppDelegate()
        app.delegate = delegate
        app.run()
    }
}
