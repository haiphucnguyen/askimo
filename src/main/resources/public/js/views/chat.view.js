document.addEventListener("alpine:init", () => {
  Alpine.data("chatView", () => ({
    messages: [],
    input: "",
    loading: false,
    ctrl: null, // AbortController

    async sendStreaming() {
      const q = this.input.trim();
      if (!q || this.loading) return;

      this.messages.push({ role: "user", content: q });
      const assistant = { role: "assistant", content: "" };
      this.messages.push(assistant);
      // Ensure assistant bubble is rendered immediately
      this.messages = [...this.messages];
      this.input = "";
      this.scroll();

      this.ctrl = new AbortController();
      this.loading = true;

      try {
        const res = await fetch("/api/chat/stream", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ message: q }),
          signal: this.ctrl.signal,
        });
        if (!res.ok) throw new Error("HTTP " + res.status);
        if (!res.body) throw new Error("No body (stream not available)");

        const reader = res.body.getReader();
        const dec = new TextDecoder();
        let total = "";

        while (true) {
          const { value, done } = await reader.read();
          if (done) break;
          const chunkText = dec.decode(value, { stream: true });
          console.debug("[chat][stream] chunk:", chunkText);
          total += chunkText;
          assistant.content += chunkText;
          // Replace the last message object to force Alpine to re-render the item
          this.messages[this.messages.length - 1] = { ...assistant };
          // Force Alpine to notice nested object change inside the array
          this.messages = [...this.messages];
          this.scroll();
        }
      } catch (e) {
        console.error("[chat] stream error:", e);
        assistant.content +=
          (assistant.content ? "\n" : "") + "⚠️ " + (e?.message || e);
        // Replace last message to force Alpine update
        this.messages[this.messages.length - 1] = { ...assistant };
        this.messages = [...this.messages];
      } finally {
        this.loading = false;
        this.ctrl = null;
        // Ensure final state is reflected in UI
        this.messages[this.messages.length - 1] = { ...assistant };
        this.messages = [...this.messages];
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
