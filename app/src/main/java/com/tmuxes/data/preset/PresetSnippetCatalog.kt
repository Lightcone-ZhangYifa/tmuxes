package com.tmuxes.data.preset

data class PresetSnippetTemplate(
    val name: String,
    val command: String
)

data class PresetLibraryTemplate(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val snippets: List<PresetSnippetTemplate>
)

object PresetSnippetCatalog {

    fun getLibrary(id: String): PresetLibraryTemplate? =
        libraries.find { it.id == id }

    // -----------------------------------------------------------------
    // tmux Session Management
    // -----------------------------------------------------------------
    private val tmux = PresetLibraryTemplate(
        id = "tmux",
        name = "tmux Sessions",
        description = "tmux session, window and pane management",
        iconName = "Dvr",
        snippets = listOf(
            PresetSnippetTemplate("tmux ls", "tmux ls"),
            PresetSnippetTemplate("tmux new -s ", "tmux new -s "),
            PresetSnippetTemplate("tmux attach -t ", "tmux attach -t "),
            PresetSnippetTemplate("tmux kill-session -t ", "tmux kill-session -t "),
            PresetSnippetTemplate("tmux rename-session ", "tmux rename-session "),
            PresetSnippetTemplate("tmux detach", "tmux detach"),
            PresetSnippetTemplate("tmux kill-server", "tmux kill-server"),
            PresetSnippetTemplate("tmux switch -t ", "tmux switch -t "),
            PresetSnippetTemplate("tmux new-window", "tmux new-window"),
            PresetSnippetTemplate("tmux split-window -h", "tmux split-window -h"),
            PresetSnippetTemplate("tmux split-window -v", "tmux split-window -v"),
            PresetSnippetTemplate("tmux select-pane -t ", "tmux select-pane -t "),
            PresetSnippetTemplate("tmux resize-pane -D 5", "tmux resize-pane -D 5"),

            // G1: Display options
            PresetSnippetTemplate("tmux set -g status off", "tmux set -g status off"),
            PresetSnippetTemplate("tmux set -g status on", "tmux set -g status on"),
            PresetSnippetTemplate("tmux set -g mouse on", "tmux set -g mouse on"),
            PresetSnippetTemplate("tmux set -g mouse off", "tmux set -g mouse off"),

            // G2: Pane layout
            PresetSnippetTemplate("tmux resize-pane -Z", "tmux resize-pane -Z"),
            PresetSnippetTemplate("tmux swap-pane -U", "tmux swap-pane -U"),
            PresetSnippetTemplate("tmux swap-pane -D", "tmux swap-pane -D"),

            // G3: Window navigation
            PresetSnippetTemplate("tmux setw synchronize-panes", "tmux setw synchronize-panes"),
            PresetSnippetTemplate("tmux move-window -t :+", "tmux move-window -t :+"),
            PresetSnippetTemplate("tmux move-window -t :-", "tmux move-window -t :-"),

            // G4: Inspection / interactive
            PresetSnippetTemplate("tmux list-windows", "tmux list-windows"),
            PresetSnippetTemplate("tmux choose-tree", "tmux choose-tree"),
            PresetSnippetTemplate("tmux current command", "tmux display-message -p '#{pane_current_command}'"),

            // G5: Config
            PresetSnippetTemplate("tmux source-file ~/.tmux.conf", "tmux source-file ~/.tmux.conf"),
            PresetSnippetTemplate("tmux show-options -g", "tmux show-options -g"),
        )
    )

    // -----------------------------------------------------------------
    // Systemd Services
    // -----------------------------------------------------------------
    private val systemd = PresetLibraryTemplate(
        id = "systemd",
        name = "Systemd Services",
        description = "Service management and journal logs",
        iconName = "MiscellaneousServices",
        snippets = listOf(
            PresetSnippetTemplate("systemctl status ", "systemctl status "),
            PresetSnippetTemplate("sudo systemctl start ", "sudo systemctl start "),
            PresetSnippetTemplate("sudo systemctl stop ", "sudo systemctl stop "),
            PresetSnippetTemplate("sudo systemctl restart ", "sudo systemctl restart "),
            PresetSnippetTemplate("sudo systemctl enable ", "sudo systemctl enable "),
            PresetSnippetTemplate("sudo systemctl disable ", "sudo systemctl disable "),
            PresetSnippetTemplate("journalctl -u  -f", "journalctl -u  -f"),
            PresetSnippetTemplate("Journal since 1h", "journalctl -u  --since '1 hour ago'"),
            PresetSnippetTemplate("List services", "systemctl list-units --type=service"),
            PresetSnippetTemplate("systemctl list-units --failed", "systemctl list-units --failed"),
            PresetSnippetTemplate("sudo systemctl daemon-reload", "sudo systemctl daemon-reload"),
        )
    )

    // -----------------------------------------------------------------
    // Docker & Compose
    // -----------------------------------------------------------------
    private val docker = PresetLibraryTemplate(
        id = "docker",
        name = "Docker & Compose",
        description = "Container and compose management",
        iconName = "Inventory2",
        snippets = listOf(
            PresetSnippetTemplate("docker ps", "docker ps"),
            PresetSnippetTemplate("docker ps -a", "docker ps -a"),
            PresetSnippetTemplate("docker images", "docker images"),
            PresetSnippetTemplate("docker logs -f ", "docker logs -f "),
            PresetSnippetTemplate("docker exec -it  /bin/bash", "docker exec -it  /bin/bash"),
            PresetSnippetTemplate("docker stop ", "docker stop "),
            PresetSnippetTemplate("docker rm ", "docker rm "),
            PresetSnippetTemplate("docker system prune -f", "docker system prune -f"),
            PresetSnippetTemplate("docker compose up -d", "docker compose up -d"),
            PresetSnippetTemplate("docker compose down", "docker compose down"),
            PresetSnippetTemplate("docker compose logs -f", "docker compose logs -f"),
            PresetSnippetTemplate("docker stats", "docker stats"),
            PresetSnippetTemplate("docker network ls", "docker network ls"),
            PresetSnippetTemplate("docker volume ls", "docker volume ls"),
        )
    )

    // -----------------------------------------------------------------
    // Files & Navigation
    // -----------------------------------------------------------------
    private val files = PresetLibraryTemplate(
        id = "files",
        name = "Files & Navigation",
        description = "File operations, search and archive",
        iconName = "Folder",
        snippets = listOf(
            PresetSnippetTemplate("ls -lah", "ls -lah"),
            PresetSnippetTemplate("du -sh *", "du -sh *"),
            PresetSnippetTemplate("df -h", "df -h"),
            PresetSnippetTemplate("find . -name ''", "find . -name ''"),
            PresetSnippetTemplate("find . -mtime -1", "find . -mtime -1"),
            PresetSnippetTemplate("tar czf archive.tar.gz ", "tar czf archive.tar.gz "),
            PresetSnippetTemplate("tar xzf archive.tar.gz", "tar xzf archive.tar.gz"),
            PresetSnippetTemplate("rsync -avz  ", "rsync -avz  "),
            PresetSnippetTemplate("chmod -R 755 ", "chmod -R 755 "),
            PresetSnippetTemplate("chown -R : ", "chown -R : "),
            PresetSnippetTemplate("ln -s  ", "ln -s  "),
            PresetSnippetTemplate("tree -L 2", "tree -L 2"),
        )
    )

    // -----------------------------------------------------------------
    // Networking
    // -----------------------------------------------------------------
    private val network = PresetLibraryTemplate(
        id = "network",
        name = "Networking",
        description = "Network diagnostics and configuration",
        iconName = "Lan",
        snippets = listOf(
            PresetSnippetTemplate("ss -tulnp", "ss -tulnp"),
            PresetSnippetTemplate("curl -I ", "curl -I "),
            PresetSnippetTemplate("ping -c 4 ", "ping -c 4 "),
            PresetSnippetTemplate("traceroute ", "traceroute "),
            PresetSnippetTemplate("dig ", "dig "),
            PresetSnippetTemplate("nslookup ", "nslookup "),
            PresetSnippetTemplate("ip addr show", "ip addr show"),
            PresetSnippetTemplate("ip route show", "ip route show"),
            PresetSnippetTemplate("netstat -tulnp", "netstat -tulnp"),
            PresetSnippetTemplate("wget ", "wget "),
            PresetSnippetTemplate("sudo iptables -L -n", "sudo iptables -L -n"),
            PresetSnippetTemplate("sudo ufw status verbose", "sudo ufw status verbose"),
            PresetSnippetTemplate("sudo tcpdump -i any port ", "sudo tcpdump -i any port "),
        )
    )

    // -----------------------------------------------------------------
    // System Monitoring
    // -----------------------------------------------------------------
    private val monitoring = PresetLibraryTemplate(
        id = "monitoring",
        name = "System Monitoring",
        description = "CPU, memory, disk and process monitoring",
        iconName = "Monitor",
        snippets = listOf(
            PresetSnippetTemplate("top", "top"),
            PresetSnippetTemplate("htop", "htop"),
            PresetSnippetTemplate("free -h", "free -h"),
            PresetSnippetTemplate("uptime", "uptime"),
            PresetSnippetTemplate("vmstat 1 5", "vmstat 1 5"),
            PresetSnippetTemplate("iostat -x 1 5", "iostat -x 1 5"),
            PresetSnippetTemplate("dmesg | tail -50", "dmesg | tail -50"),
            PresetSnippetTemplate("CPU info", "cat /proc/cpuinfo | head -20"),
            PresetSnippetTemplate("lsblk", "lsblk"),
            PresetSnippetTemplate("Top memory procs", "ps aux --sort=-%mem | head -20"),
            PresetSnippetTemplate("Top CPU procs", "ps aux --sort=-%cpu | head -20"),
            PresetSnippetTemplate("w", "w"),
            PresetSnippetTemplate("sar -u 1 5", "sar -u 1 5"),
        )
    )

    // -----------------------------------------------------------------
    // Git Operations
    // -----------------------------------------------------------------
    private val git = PresetLibraryTemplate(
        id = "git",
        name = "Git Operations",
        description = "Common git commands for version control",
        iconName = "Code",
        snippets = listOf(
            PresetSnippetTemplate("git status", "git status"),
            PresetSnippetTemplate("git log --oneline -20", "git log --oneline -20"),
            PresetSnippetTemplate("git diff", "git diff"),
            PresetSnippetTemplate("git branch -a", "git branch -a"),
            PresetSnippetTemplate("git pull", "git pull"),
            PresetSnippetTemplate("git push", "git push"),
            PresetSnippetTemplate("git stash", "git stash"),
            PresetSnippetTemplate("git stash pop", "git stash pop"),
            PresetSnippetTemplate("git checkout ", "git checkout "),
            PresetSnippetTemplate("git merge ", "git merge "),
            PresetSnippetTemplate("git fetch --all", "git fetch --all"),
            PresetSnippetTemplate("git log --graph --oneline --all", "git log --graph --oneline --all"),
            PresetSnippetTemplate("git remote -v", "git remote -v"),
        )
    )

    // -----------------------------------------------------------------
    // Package Management
    // -----------------------------------------------------------------
    private val packageMgr = PresetLibraryTemplate(
        id = "package_mgr",
        name = "Package Management",
        description = "apt, yum and dpkg package operations",
        iconName = "GetApp",
        snippets = listOf(
            PresetSnippetTemplate("apt update + upgrade", "sudo apt update && sudo apt upgrade -y"),
            PresetSnippetTemplate("sudo apt install ", "sudo apt install "),
            PresetSnippetTemplate("apt search ", "apt search "),
            PresetSnippetTemplate("dpkg -l | grep ", "dpkg -l | grep "),
            PresetSnippetTemplate("sudo yum update -y", "sudo yum update -y"),
            PresetSnippetTemplate("sudo yum install ", "sudo yum install "),
            PresetSnippetTemplate("yum search ", "yum search "),
            PresetSnippetTemplate("rpm -qa | grep ", "rpm -qa | grep "),
            PresetSnippetTemplate("apt list --installed", "apt list --installed"),
            PresetSnippetTemplate("sudo apt autoremove -y", "sudo apt autoremove -y"),
        )
    )

    // -----------------------------------------------------------------
    // Log Analysis
    // -----------------------------------------------------------------
    private val logs = PresetLibraryTemplate(
        id = "logs",
        name = "Log Analysis",
        description = "System and application log viewing",
        iconName = "Article",
        snippets = listOf(
            PresetSnippetTemplate("tail -f /var/log/syslog", "tail -f /var/log/syslog"),
            PresetSnippetTemplate("tail -f /var/log/auth.log", "tail -f /var/log/auth.log"),
            PresetSnippetTemplate("Nginx errors", "tail -100 /var/log/nginx/error.log"),
            PresetSnippetTemplate("Grep errors", "grep -r 'ERROR' /var/log/"),
            PresetSnippetTemplate("Read rotated log", "zcat /var/log/syslog.1.gz | tail -100"),
            PresetSnippetTemplate("journalctl -f", "journalctl -f"),
            PresetSnippetTemplate("journalctl --since '1 hour ago'", "journalctl --since '1 hour ago'"),
            PresetSnippetTemplate("journalctl -p err", "journalctl -p err"),
            PresetSnippetTemplate("sudo cat /var/log/secure", "sudo cat /var/log/secure"),
            PresetSnippetTemplate("dmesg --follow", "dmesg --follow"),
            PresetSnippetTemplate("tail -50 /var/log/syslog", "tail -50 /var/log/syslog"),
        )
    )

    // -----------------------------------------------------------------
    // Security & Users
    // -----------------------------------------------------------------
    private val security = PresetLibraryTemplate(
        id = "security",
        name = "Security & Users",
        description = "User management, permissions and SSH keys",
        iconName = "Security",
        snippets = listOf(
            PresetSnippetTemplate("whoami", "whoami"),
            PresetSnippetTemplate("id", "id"),
            PresetSnippetTemplate("sudo su -", "sudo su -"),
            PresetSnippetTemplate("passwd", "passwd"),
            PresetSnippetTemplate("sudo useradd -m ", "sudo useradd -m "),
            PresetSnippetTemplate("sudo userdel -r ", "sudo userdel -r "),
            PresetSnippetTemplate("groups ", "groups "),
            PresetSnippetTemplate("last -10", "last -10"),
            PresetSnippetTemplate("faillog -a", "faillog -a"),
            PresetSnippetTemplate("sudo visudo", "sudo visudo"),
            PresetSnippetTemplate("ssh-keygen -t ed25519", "ssh-keygen -t ed25519"),
            PresetSnippetTemplate("Fix SSH key perms", "chmod 600 ~/.ssh/authorized_keys"),
            PresetSnippetTemplate("cat ~/.ssh/authorized_keys", "cat ~/.ssh/authorized_keys"),
        )
    )

    // -----------------------------------------------------------------
    // Python Dev
    // -----------------------------------------------------------------
    private val pythonDev = PresetLibraryTemplate(
        id = "python_dev",
        name = "Python Dev",
        description = "Python virtual environments and package management",
        iconName = "Build",
        snippets = listOf(
            PresetSnippetTemplate("python -m venv .venv", "python -m venv .venv"),
            PresetSnippetTemplate("source .venv/bin/activate", "source .venv/bin/activate"),
            PresetSnippetTemplate("deactivate", "deactivate"),
            PresetSnippetTemplate("pip install -r requirements.txt", "pip install -r requirements.txt"),
            PresetSnippetTemplate("Freeze requirements", "pip freeze > requirements.txt"),
            PresetSnippetTemplate("pip install ", "pip install "),
            PresetSnippetTemplate("pip list", "pip list"),
            PresetSnippetTemplate("pip install --upgrade pip", "pip install --upgrade pip"),
            PresetSnippetTemplate("Create conda env", "conda create -n env python=3.11"),
            PresetSnippetTemplate("conda activate ", "conda activate "),
            PresetSnippetTemplate("jupyter notebook", "jupyter notebook"),
            PresetSnippetTemplate("jupyter lab", "jupyter lab"),
            PresetSnippetTemplate("Install ipykernel", "python -m ipykernel install --user --name=env"),
            PresetSnippetTemplate("python --version", "python --version"),
        )
    )

    // -----------------------------------------------------------------
    // Public catalog
    // -----------------------------------------------------------------
    val libraries: List<PresetLibraryTemplate> = listOf(
        tmux, systemd, docker, files, network,
        monitoring, git, packageMgr, logs, security,
        pythonDev
    )
}
