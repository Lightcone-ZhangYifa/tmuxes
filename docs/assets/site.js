(() => {
  const root = document.documentElement;
  const body = document.body;
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const langButtons = [...document.querySelectorAll("[data-lang-choice]")];
  const readmeLink = document.querySelector("#readme-link");
  const progressBar = document.querySelector("#progress-bar");
  const heroTerminal = document.querySelector("#hero-terminal");

  const terminalLines = {
    zh: [
      ["$ ssh prod-bastion", "host key verified"],
      ["$ tmux attach -t agent-dashboard", "session resurrected"],
      ["watch ./scripts/mobile-status-board", "launcher widget online"],
      ["forward 127.0.0.1:3000 → internal:3000", "private preview open"],
      ["Codex CLI + Claude Code", "still running in tmux"]
    ],
    en: [
      ["$ ssh prod-bastion", "host key verified"],
      ["$ tmux attach -t agent-dashboard", "session resurrected"],
      ["watch ./scripts/mobile-status-board", "launcher widget online"],
      ["forward 127.0.0.1:3000 → internal:3000", "private preview open"],
      ["Codex CLI + Claude Code", "still running in tmux"]
    ]
  };

  const nodeCopy = {
    android: {
      label: "selected / android",
      zhTitle: "Android 端是控制面，不拥有远程状态。",
      enTitle: "Android is the control surface, not the owner of remote state.",
      zhText: "它负责输入、渲染、磁贴、Quick Settings 和转发入口；真正的长任务仍在远端 tmux session 中。",
      enText: "It owns input, rendering, widgets, Quick Settings, and forwarding entry points; long-running work remains in remote tmux sessions."
    },
    bastion: {
      label: "selected / bastion",
      zhTitle: "父服务器是通路，不是把子机混成同一个 host。",
      enTitle: "The parent is the path, not a way to blur hosts together.",
      zhText: "父服务器通过 SSH direct-tcpip 为子服务器提供 ProxyJump 风格通道。子服务器仍有自己的认证、host key、keepalive 和 session。",
      enText: "The parent uses SSH direct-tcpip to provide a ProxyJump-style path. The child keeps its own auth, host key, keepalive, and sessions."
    },
    internal: {
      label: "selected / internal-host",
      zhTitle: "内网主机仍是完整的一等服务器。",
      enTitle: "An internal host remains a first-class server.",
      zhText: "它不是父服务器的一条备注，而是有自己凭据、转发规则、tmux session 和失败状态的对象。",
      enText: "It is not a note under the parent; it owns credentials, forwarding rules, tmux sessions, and failure state."
    },
    preview: {
      label: "selected / forward",
      zhTitle: "端口转发要能验证结果。",
      enTitle: "Forwarding should show the result.",
      zhText: "远端 localhost 服务通过同一条 SSH 信任边界进入手机浏览器。页面强调“能打开”，不是只展示配置表单。",
      enText: "A remote localhost service reaches the phone browser through the same SSH trust boundary. The page emphasizes the visible result, not just the config form."
    }
  };

  function setLanguage(lang) {
    const normalized = lang === "en" ? "en" : "zh";
    body.dataset.lang = normalized;
    document.documentElement.lang = normalized === "zh" ? "zh-CN" : "en";
    localStorage.setItem("tmuxes-site-language", normalized);

    langButtons.forEach((button) => {
      const active = button.dataset.langChoice === normalized;
      button.setAttribute("aria-pressed", String(active));
    });

    if (readmeLink) {
      readmeLink.href = normalized === "zh"
        ? "https://github.com/Lightcone-ZhangYifa/tmuxes/blob/main/README.zh-CN.md"
        : "https://github.com/Lightcone-ZhangYifa/tmuxes/blob/main/README.md";
    }

    renderTerminal(normalized);
    updateNodeCopy(document.querySelector(".node.active")?.dataset.node || "android");
  }

  function renderTerminal(lang) {
    if (!heroTerminal) return;
    const lines = terminalLines[lang] || terminalLines.zh;
    const out = lines
      .map(([cmd, status]) => `${cmd.padEnd(52, " ")}  ${status}`)
      .join("\n");
    heroTerminal.textContent = out;
  }

  function updateProgress() {
    if (!progressBar) return;
    const doc = document.documentElement;
    const max = Math.max(1, doc.scrollHeight - window.innerHeight);
    const percent = Math.min(100, Math.max(0, (window.scrollY / max) * 100));
    progressBar.style.width = `${percent}%`;
  }

  function setupReveal() {
    const targets = [...document.querySelectorAll("[data-reveal]")];
    if (!targets.length) return;

    if (reduceMotion || !("IntersectionObserver" in window)) {
      targets.forEach((target) => target.classList.add("visible"));
      return;
    }

    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("visible");
          observer.unobserve(entry.target);
        }
      });
    }, { rootMargin: "0px 0px -12% 0px", threshold: 0.08 });

    targets.forEach((target) => observer.observe(target));
  }

  function setupPointerLight() {
    if (reduceMotion) return;

    window.addEventListener("pointermove", (event) => {
      root.style.setProperty("--mx", (event.clientX / window.innerWidth).toFixed(4));
      root.style.setProperty("--my", (event.clientY / window.innerHeight).toFixed(4));
    }, { passive: true });
  }

  function setupSceneTabs() {
    const tabs = [...document.querySelectorAll(".scene-tab")];
    const panels = [...document.querySelectorAll("[data-scene-panel]")];
    if (!tabs.length || !panels.length) return;

    function activate(scene) {
      tabs.forEach((tab) => {
        const active = tab.dataset.scene === scene;
        tab.classList.toggle("active", active);
        tab.setAttribute("aria-selected", String(active));
      });
      panels.forEach((panel) => {
        panel.classList.toggle("active", panel.dataset.scenePanel === scene);
      });
    }

    tabs.forEach((tab) => {
      tab.addEventListener("click", () => activate(tab.dataset.scene));
      tab.addEventListener("keydown", (event) => {
        if (!["ArrowRight", "ArrowLeft"].includes(event.key)) return;
        event.preventDefault();
        const index = tabs.indexOf(tab);
        const next = event.key === "ArrowRight"
          ? tabs[(index + 1) % tabs.length]
          : tabs[(index - 1 + tabs.length) % tabs.length];
        next.focus();
        activate(next.dataset.scene);
      });
    });
  }

  function updateNodeCopy(nodeName) {
    const copy = nodeCopy[nodeName] || nodeCopy.android;
    const target = document.querySelector("#node-copy");
    if (!target) return;
    target.innerHTML = `
      <p class="mono-label">${copy.label}</p>
      <h3><span class="zh">${copy.zhTitle}</span><span class="en">${copy.enTitle}</span></h3>
      <p><span class="zh">${copy.zhText}</span><span class="en">${copy.enText}</span></p>
    `;
  }

  function setupTopology() {
    const nodes = [...document.querySelectorAll(".node")];
    if (!nodes.length) return;

    nodes.forEach((node) => {
      node.addEventListener("click", () => {
        nodes.forEach((item) => item.classList.remove("active"));
        node.classList.add("active");
        updateNodeCopy(node.dataset.node);
      });
    });
  }

  function setupTilt() {
    const tilt = document.querySelector("[data-tilt]");
    if (!tilt || reduceMotion) return;

    tilt.addEventListener("pointermove", (event) => {
      const box = tilt.getBoundingClientRect();
      const mx = (event.clientX - box.left) / box.width;
      const my = (event.clientY - box.top) / box.height;
      tilt.style.setProperty("--mx", mx.toFixed(4));
      tilt.style.setProperty("--my", my.toFixed(4));
    }, { passive: true });

    tilt.addEventListener("pointerleave", () => {
      tilt.style.setProperty("--mx", ".5");
      tilt.style.setProperty("--my", ".5");
    });
  }

  function setupCanvas() {
    const canvas = document.querySelector("#field");
    if (!canvas || reduceMotion) return;
    const context = canvas.getContext("2d", { alpha: true });
    if (!context) return;

    let width = 0;
    let height = 0;
    let dpr = 1;
    let particles = [];

    function resize() {
      dpr = Math.min(2, window.devicePixelRatio || 1);
      width = window.innerWidth;
      height = window.innerHeight;
      canvas.width = Math.floor(width * dpr);
      canvas.height = Math.floor(height * dpr);
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
      context.setTransform(dpr, 0, 0, dpr, 0, 0);

      const count = Math.max(42, Math.min(90, Math.floor((width * height) / 22000)));
      particles = Array.from({ length: count }, (_, index) => ({
        x: Math.random() * width,
        y: Math.random() * height,
        vx: (Math.random() - .5) * .18,
        vy: (Math.random() - .5) * .18,
        r: index % 8 === 0 ? 1.5 : 1
      }));
    }

    function frame() {
      context.clearRect(0, 0, width, height);

      for (const p of particles) {
        p.x += p.vx;
        p.y += p.vy;
        if (p.x < -20) p.x = width + 20;
        if (p.x > width + 20) p.x = -20;
        if (p.y < -20) p.y = height + 20;
        if (p.y > height + 20) p.y = -20;
      }

      for (let i = 0; i < particles.length; i += 1) {
        const a = particles[i];
        for (let j = i + 1; j < particles.length; j += 1) {
          const b = particles[j];
          const dx = a.x - b.x;
          const dy = a.y - b.y;
          const dist = Math.hypot(dx, dy);
          if (dist < 148) {
            const alpha = (1 - dist / 148) * .14;
            context.strokeStyle = `rgba(107, 232, 255, ${alpha})`;
            context.lineWidth = 1;
            context.beginPath();
            context.moveTo(a.x, a.y);
            context.lineTo(b.x, b.y);
            context.stroke();
          }
        }
      }

      for (const p of particles) {
        context.fillStyle = "rgba(220, 247, 255, .42)";
        context.beginPath();
        context.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        context.fill();
      }

      requestAnimationFrame(frame);
    }

    resize();
    window.addEventListener("resize", resize, { passive: true });
    requestAnimationFrame(frame);
  }

  const preferred = localStorage.getItem("tmuxes-site-language")
    || (navigator.language && navigator.language.toLowerCase().startsWith("zh") ? "zh" : "en");

  langButtons.forEach((button) => {
    button.addEventListener("click", () => setLanguage(button.dataset.langChoice));
  });

  window.addEventListener("scroll", updateProgress, { passive: true });
  window.addEventListener("resize", updateProgress, { passive: true });

  setLanguage(preferred);
  updateProgress();
  setupReveal();
  setupPointerLight();
  setupSceneTabs();
  setupTopology();
  setupTilt();
  setupCanvas();
})();
