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

    private val codex = PresetLibraryTemplate(
        id = "codex",
        name = "Codex CLI",
        description = "Interactive and non-interactive Codex workflows",
        iconName = "Terminal",
        snippets = listOf(
            PresetSnippetTemplate("Open Codex here", "codex"),
            PresetSnippetTemplate("Resume last session", "codex resume --last"),
            PresetSnippetTemplate("Review current tree", "codex review"),
            PresetSnippetTemplate("Explain repo shape", "codex exec \"Summarize this repository structure and the next useful command to run.\""),
            PresetSnippetTemplate("Find risky diff", "codex exec \"Review the current git diff for bugs, regressions, and missing tests.\""),
            PresetSnippetTemplate("Plan focused fix", "codex exec \"Inspect the failing area and propose the smallest root-cause fix before editing.\""),
            PresetSnippetTemplate("Use workspace sandbox", "codex --sandbox workspace-write"),
            PresetSnippetTemplate("Run in this dir", "codex --cd ."),
            PresetSnippetTemplate("Attach screenshot", "codex -i "),
            PresetSnippetTemplate("Doctor", "codex doctor"),
            PresetSnippetTemplate("Version", "codex --version"),
        )
    )

    private val claudeCode = PresetLibraryTemplate(
        id = "claude_code",
        name = "Claude Code",
        description = "Claude Code sessions, reviews and print-mode prompts",
        iconName = "Code",
        snippets = listOf(
            PresetSnippetTemplate("Open Claude Code", "claude"),
            PresetSnippetTemplate("Continue here", "claude --continue"),
            PresetSnippetTemplate("Resume picker", "claude --resume"),
            PresetSnippetTemplate("Quick repo summary", "claude --print \"Summarize the current repository and list the highest-risk files.\""),
            PresetSnippetTemplate("Focused code review", "claude --print \"Review the current git diff for correctness, regressions, and missing tests.\""),
            PresetSnippetTemplate("Sonnet session", "claude --model sonnet"),
            PresetSnippetTemplate("Opus session", "claude --model opus"),
            PresetSnippetTemplate("Allow extra dir", "claude --add-dir "),
            PresetSnippetTemplate("Accept edits mode", "claude --permission-mode acceptEdits"),
            PresetSnippetTemplate("Doctor", "claude doctor"),
            PresetSnippetTemplate("Version", "claude --version"),
        )
    )

    private val tmux = PresetLibraryTemplate(
        id = "tmux",
        name = "tmux",
        description = "Durable mobile sessions, panes and capture commands",
        iconName = "Dvr",
        snippets = listOf(
            PresetSnippetTemplate("Attach or create main", "tmux new-session -A -s main"),
            PresetSnippetTemplate("List sessions compact", "tmux list-sessions -F '#{session_name}: #{session_windows} windows, attached=#{session_attached}'"),
            PresetSnippetTemplate("Attach target", "tmux attach-session -t "),
            PresetSnippetTemplate("Detach client", "tmux detach-client"),
            PresetSnippetTemplate("Rename session", "tmux rename-session -t  "),
            PresetSnippetTemplate("New window here", "tmux new-window -c '#{pane_current_path}'"),
            PresetSnippetTemplate("Split right here", "tmux split-window -h -c '#{pane_current_path}'"),
            PresetSnippetTemplate("Split below here", "tmux split-window -v -c '#{pane_current_path}'"),
            PresetSnippetTemplate("Zoom pane", "tmux resize-pane -Z"),
            PresetSnippetTemplate("Tiled layout", "tmux select-layout tiled"),
            PresetSnippetTemplate("Sync panes on", "tmux setw synchronize-panes on"),
            PresetSnippetTemplate("Sync panes off", "tmux setw synchronize-panes off"),
            PresetSnippetTemplate("Capture last 200 lines", "tmux capture-pane -p -S -200"),
            PresetSnippetTemplate("Show pane command", "tmux display-message -p '#{pane_current_command} #{pane_current_path}'"),
            PresetSnippetTemplate("Reload config", "tmux source-file ~/.tmux.conf"),
        )
    )

    private val ssh = PresetLibraryTemplate(
        id = "ssh",
        name = "SSH",
        description = "Reliable remote login, tunnels, multiplexing and transfers",
        iconName = "Security",
        snippets = listOf(
            PresetSnippetTemplate("Attach remote tmux", "ssh -t  'tmux new-session -A -s main'"),
            PresetSnippetTemplate("Keepalive login", "ssh -o ServerAliveInterval=30 -o ServerAliveCountMax=3 "),
            PresetSnippetTemplate("Batch auth check", "ssh -o BatchMode=yes -o ConnectTimeout=10  true"),
            PresetSnippetTemplate("Debug key auth", "ssh -vvv -o PreferredAuthentications=publickey "),
            PresetSnippetTemplate("Jump host", "ssh -J jump-host target-host"),
            PresetSnippetTemplate("Local port forward", "ssh -N -L 127.0.0.1:8080:127.0.0.1:80 "),
            PresetSnippetTemplate("Remote port forward", "ssh -N -R 127.0.0.1:8080:127.0.0.1:8080 "),
            PresetSnippetTemplate("SOCKS tunnel", "ssh -N -D 127.0.0.1:1080 "),
            PresetSnippetTemplate("Open control master", "ssh -MNf -S ~/.ssh/cm-%r@%h:%p "),
            PresetSnippetTemplate("Check control master", "ssh -S ~/.ssh/cm-%r@%h:%p -O check "),
            PresetSnippetTemplate("Close control master", "ssh -S ~/.ssh/cm-%r@%h:%p -O exit "),
            PresetSnippetTemplate("Remote health snapshot", "ssh  'hostname; uptime; df -h /; free -h'"),
            PresetSnippetTemplate("SCP upload", "scp -r  :/path/"),
            PresetSnippetTemplate("Rsync upload progress", "rsync -azP --info=progress2 ./ :/path/"),
            PresetSnippetTemplate("Rsync download progress", "rsync -azP --info=progress2 :/path/ ./"),
            PresetSnippetTemplate("Remove stale host key", "ssh-keygen -R "),
            PresetSnippetTemplate("Scan host key", "ssh-keyscan -H "),
        )
    )

    private val git = PresetLibraryTemplate(
        id = "git",
        name = "Git",
        description = "Branch hygiene, review diffs, stashes and worktrees",
        iconName = "Code",
        snippets = listOf(
            PresetSnippetTemplate("Status short branch", "git status --short --branch"),
            PresetSnippetTemplate("Recent graph", "git log --graph --decorate --oneline --date=relative --all -30"),
            PresetSnippetTemplate("Fetch prune tags", "git fetch --all --prune --tags"),
            PresetSnippetTemplate("Pull fast-forward only", "git pull --ff-only"),
            PresetSnippetTemplate("Push current branch", "git push -u origin HEAD"),
            PresetSnippetTemplate("Diff check summary", "git diff --stat && git diff --check"),
            PresetSnippetTemplate("Staged patch", "git diff --cached --stat && git diff --cached"),
            PresetSnippetTemplate("Files vs upstream", "git diff --name-status @{upstream}...HEAD"),
            PresetSnippetTemplate("Branches by activity", "git branch --sort=-committerdate --format='%(committerdate:relative) %(refname:short)'"),
            PresetSnippetTemplate("Switch new branch", "git switch -c "),
            PresetSnippetTemplate("Stash with untracked", "git stash push -u -m \"\""),
            PresetSnippetTemplate("Inspect latest stash", "git stash show --stat --patch stash@{0}"),
            PresetSnippetTemplate("List worktrees", "git worktree list"),
            PresetSnippetTemplate("Add worktree branch", "git worktree add ../  -b "),
            PresetSnippetTemplate("Pickaxe search", "git log -S'' --source --all -- "),
            PresetSnippetTemplate("Blame function", "git blame -L :function_name -- file"),
        )
    )

    private val docker = PresetLibraryTemplate(
        id = "docker",
        name = "Docker",
        description = "Compose operations, container inspection and reproducible runs",
        iconName = "Inventory2",
        snippets = listOf(
            PresetSnippetTemplate("Compose status", "docker compose ps"),
            PresetSnippetTemplate("Compose config", "docker compose config"),
            PresetSnippetTemplate("Compose up rebuild", "docker compose up -d --build"),
            PresetSnippetTemplate("Compose logs recent", "docker compose logs -f --tail=200"),
            PresetSnippetTemplate("Compose shell", "docker compose exec  sh"),
            PresetSnippetTemplate("Compose down orphans", "docker compose down --remove-orphans"),
            PresetSnippetTemplate("Containers compact", "docker ps --format 'table {{.Names}}\\t{{.Image}}\\t{{.Status}}\\t{{.Ports}}'"),
            PresetSnippetTemplate("Container logs recent", "docker logs -f --tail=200 "),
            PresetSnippetTemplate("Container shell with env", "docker exec -it  sh -lc 'id && pwd && env | sort'"),
            PresetSnippetTemplate("Stats snapshot", "docker stats --no-stream"),
            PresetSnippetTemplate("Health check status", "docker inspect --format '{{.State.Health.Status}}' "),
            PresetSnippetTemplate("Published ports", "docker port "),
            PresetSnippetTemplate("Image disk usage", "docker system df -v"),
            PresetSnippetTemplate("Build plain logs", "DOCKER_BUILDKIT=1 docker build --progress=plain -t  ."),
            PresetSnippetTemplate("Run current dir", "docker run --rm -it -v \"\$PWD\":/work -w /work  sh"),
        )
    )

    private val python = PresetLibraryTemplate(
        id = "python",
        name = "Python",
        description = "Project setup, tests, formatting and local tools",
        iconName = "Build",
        snippets = listOf(
            PresetSnippetTemplate("Create venv", "python3 -m venv .venv && . .venv/bin/activate"),
            PresetSnippetTemplate("Activate venv", ". .venv/bin/activate"),
            PresetSnippetTemplate("Upgrade packaging", "python -m pip install -U pip wheel setuptools"),
            PresetSnippetTemplate("Install requirements", "python -m pip install -r requirements.txt"),
            PresetSnippetTemplate("Install editable", "python -m pip install -e ."),
            PresetSnippetTemplate("Freeze exact deps", "python -m pip freeze > requirements.txt"),
            PresetSnippetTemplate("Run tests quiet", "python -m pytest -q"),
            PresetSnippetTemplate("Run one test", "python -m pytest -q tests/test_file.py::test_name"),
            PresetSnippetTemplate("Stop after first fail", "python -m pytest -q -x --maxfail=1"),
            PresetSnippetTemplate("Ruff check", "python -m ruff check ."),
            PresetSnippetTemplate("Ruff format", "python -m ruff format ."),
            PresetSnippetTemplate("Type check", "python -m mypy ."),
            PresetSnippetTemplate("Serve current dir", "python -m http.server 8000"),
            PresetSnippetTemplate("Kernel for notebooks", "python -m ipykernel install --user --name tmuxes-work"),
        )
    )

    private val slurm = PresetLibraryTemplate(
        id = "slurm",
        name = "Slurm",
        description = "HPC queue inspection, interactive jobs and accounting",
        iconName = "Monitor",
        snippets = listOf(
            PresetSnippetTemplate("Cluster summary", "sinfo -o '%20P %8a %10l %6D %6t %N'"),
            PresetSnippetTemplate("My queue", "squeue -u \$USER -o '%.18i %.9P %.30j %.2t %.10M %.6D %R'"),
            PresetSnippetTemplate("Watch my queue", "watch -n 5 \"squeue -u \$USER\""),
            PresetSnippetTemplate("Interactive CPU shell", "srun --pty -p  -t 02:00:00 --mem=8G bash -l"),
            PresetSnippetTemplate("Interactive GPU shell", "srun --pty -p  --gres=gpu:1 -t 02:00:00 --mem=16G bash -l"),
            PresetSnippetTemplate("Submit batch", "sbatch "),
            PresetSnippetTemplate("Cancel job", "scancel "),
            PresetSnippetTemplate("Job details", "scontrol show job "),
            PresetSnippetTemplate("Recent accounting", "sacct -X -S today -u \$USER --format=JobID,JobName%30,State,Elapsed,MaxRSS,AllocCPUS"),
            PresetSnippetTemplate("One job accounting", "sacct -X -j  --format=JobID,JobName%30,State,Elapsed,MaxRSS,ExitCode"),
            PresetSnippetTemplate("Follow output", "tail -f slurm-*.out"),
            PresetSnippetTemplate("Show node", "scontrol show node "),
            PresetSnippetTemplate("Partition nodes", "sinfo -p  -Nel"),
            PresetSnippetTemplate("Estimate fairshare", "sshare -u \$USER"),
        )
    )

    private val apt = PresetLibraryTemplate(
        id = "apt",
        name = "APT",
        description = "Debian and Ubuntu package diagnosis and maintenance",
        iconName = "GetApp",
        snippets = listOf(
            PresetSnippetTemplate("Refresh package index", "sudo apt update"),
            PresetSnippetTemplate("Show upgrades", "apt list --upgradable"),
            PresetSnippetTemplate("Install minimal package", "sudo apt install --no-install-recommends "),
            PresetSnippetTemplate("Package policy", "apt-cache policy "),
            PresetSnippetTemplate("Search names", "apt-cache search --names-only "),
            PresetSnippetTemplate("Show package", "apt show "),
            PresetSnippetTemplate("List package files", "dpkg -L "),
            PresetSnippetTemplate("Which package owns file", "dpkg -S "),
            PresetSnippetTemplate("Hold package", "sudo apt-mark hold "),
            PresetSnippetTemplate("Unhold package", "sudo apt-mark unhold "),
            PresetSnippetTemplate("Fix broken install", "sudo apt-get install -f"),
            PresetSnippetTemplate("Finish dpkg configure", "sudo dpkg --configure -a"),
            PresetSnippetTemplate("Clean unused packages", "sudo apt autoremove --purge"),
        )
    )

    private val conda = PresetLibraryTemplate(
        id = "conda",
        name = "Conda",
        description = "Reproducible Conda environments and Python kernels",
        iconName = "Cloud",
        snippets = listOf(
            PresetSnippetTemplate("List envs", "conda info --envs"),
            PresetSnippetTemplate("Create Python env", "conda create -n  python=3.11"),
            PresetSnippetTemplate("Activate env", "conda activate "),
            PresetSnippetTemplate("Install from conda-forge", "conda install -c conda-forge "),
            PresetSnippetTemplate("Install env file", "conda env create -f environment.yml"),
            PresetSnippetTemplate("Update env from file", "conda env update -f environment.yml --prune"),
            PresetSnippetTemplate("Export direct deps", "conda env export --from-history > environment.yml"),
            PresetSnippetTemplate("Export locked env", "conda env export > environment.lock.yml"),
            PresetSnippetTemplate("List packages", "conda list"),
            PresetSnippetTemplate("Run in env", "conda run -n  python -V"),
            PresetSnippetTemplate("Remove env", "conda env remove -n "),
            PresetSnippetTemplate("Clean package cache", "conda clean -a"),
        )
    )

    val libraries: List<PresetLibraryTemplate> = listOf(
        codex,
        claudeCode,
        tmux,
        ssh,
        git,
        docker,
        python,
        slurm,
        apt,
        conda
    )
}
