document.addEventListener("alpine:init", () => {
  Alpine.data("chatView", () => ({
    messages: [],
    input: "",
    composing: false, // IME state
    loading: false,
    ctrl: null,
    now: performance.now(),
    _tickId: null,
    _renderTimer: null,

    // ✅ Unified key handler for Enter behavior
    handleEditorKeydown(e) {
      if (this.composing) return; // don't act during IME composition
      if (e.key !== "Enter") return;

      // Cmd/Ctrl+Enter → send
      if (e.metaKey || e.ctrlKey) {
        e.preventDefault();
        this.sendStreaming();
        return;
      }

      // Shift+Enter → newline (let it pass)
      if (e.shiftKey) return;

      // Bare Enter → send
      e.preventDefault();
      this.sendStreaming();
    },

    _startClock() {
      if (this._tickId) return;
      this._tickId = setInterval(() => {
        this.now = performance.now();
      }, 100);
    },
    _stopClock() {
      if (this._tickId) clearInterval(this._tickId);
      this._tickId = null;
    },

    // Heuristic if server header isn't available
    _looksLikeMarkdown(text) {
      return /(^|\n)\s{0,3}#{1,6}\s|\n```|^\s*[-*+]\s|\[[^\]]+\]\([^)]+\)|\|.+\|/m.test(
        text
      );
    },

    _renderMarkdown(assistant) {
      // Close an unfinished code fence for preview
      const ticks = (assistant.raw.match(/```/g) || []).length;
      const needsClose = ticks % 2 === 1;
      const preview = needsClose ? assistant.raw + "\n```" : assistant.raw;

      const html = DOMPurify.sanitize(marked.parse(preview));
      assistant.html = needsClose
        ? html.replace(/<pre><code[^>]*>[\s\S]*$/, (m) => m)
        : html;

      // Sync Alpine
      this.messages[this.messages.length - 1] = { ...assistant };
      this.messages = [...this.messages];
    },

    _scheduleRender(assistant, ms = 120) {
      if (this._renderTimer) clearTimeout(this._renderTimer);
      this._renderTimer = setTimeout(() => this._renderMarkdown(assistant), ms);
    },

    async sendStreaming() {
      const q = this.input.trim();
      if (!q || this.loading) return;

      // user bubble
      this.messages.push({ role: "user", content: q });

      // assistant bubble (raw + html + meta)
      const assistant = {
        role: "assistant",
        content: "", // keep for plain text fallback
        raw: "",
        html: "",
        meta: {
          startAt: performance.now(),
          firstByteAt: null,
          finishAt: null,
          format: "text",
        },
      };
      this.messages.push(assistant);
      this.messages = [...this.messages];
      this.input = "";
      this.scroll();

      this.ctrl = new AbortController();
      this.loading = true;
      this._startClock();

      try {
        const res = await fetch("/api/chat/stream", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ message: q }),
          signal: this.ctrl.signal,
        });
        if (!res.ok) throw new Error("HTTP " + res.status);
        if (!res.body) throw new Error("No body (stream not available)");

        // Decide format from headers (preferred) or heuristic
        const ct = res.headers.get("content-type") || "";
        const fmtHeader = res.headers.get("x-askimo-format") || "";
        if (ct.includes("markdown") || fmtHeader.toLowerCase() === "markdown") {
          assistant.meta.format = "markdown";
        }

        const reader = res.body.getReader();
        const dec = new TextDecoder();
        let sawFirst = false;

        while (true) {
          const { value, done } = await reader.read();
          if (done) break;

          const chunkText = dec.decode(value, { stream: true });
          if (!sawFirst && chunkText.length) {
            assistant.meta.firstByteAt = performance.now();
            sawFirst = true;
          }

          assistant.raw += chunkText;

          // If server didn't label it, use heuristic after some content arrives
          if (
            assistant.meta.format === "text" &&
            this._looksLikeMarkdown(assistant.raw)
          ) {
            assistant.meta.format = "markdown";
          }

          if (assistant.meta.format === "markdown") {
            this._scheduleRender(assistant); // debounced re-render
          } else {
            // plain text fallback
            assistant.content = assistant.raw;
            this.messages[this.messages.length - 1] = { ...assistant };
            this.messages = [...this.messages];
          }

          this.scroll();
        }
      } catch (e) {
        assistant.raw +=
          (assistant.raw ? "\n" : "") + "⚠️ " + (e?.message || e);
        if (assistant.meta.format === "markdown")
          this._renderMarkdown(assistant);
        else {
          assistant.content = assistant.raw;
          this.messages[this.messages.length - 1] = { ...assistant };
          this.messages = [...this.messages];
        }
      } finally {
        assistant.meta.finishAt = performance.now();

        // Final render
        if (assistant.meta.format === "markdown")
          this._renderMarkdown(assistant);
        else {
          assistant.content = assistant.raw;
          this.messages[this.messages.length - 1] = { ...assistant };
          this.messages = [...this.messages];
        }

        this.loading = false;
        this.ctrl = null;
        this._stopClock();
        this.scroll();
      }
    },

    stop() {
      if (this.ctrl) this.ctrl.abort();
    },

    scroll() {
      this.$nextTick(() => {
        const box = this.$refs.messages;
        if (box) box.scrollTop = box.scrollHeight;
      });
    },
  }));
});
