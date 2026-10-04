import { $, el, input } from "../../core/dom.js";
import { syncComposer } from "./composer.js";

const emojis = "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 😉 😍 🥰 😘 😋 😜 🤪 😎 🤩 🥳 😏 😌 😴 🤔 🤗 🤭 🙄 😬 😢 😭 😤 😡 🥺 😱 🤯 😳 🤒 👍 👎 👏 🙌 🙏 💪 👋 🤝 ✌️ 🤞 👌 🔥 ✨ 🎉 💯 ❤️ 🧡 💛 💚 💙 💜 🖤 💔 💕 🎂 🎁 ☕ 🍕 🍔 🌹 🌙 ☀️ ⭐ ⚡".split(" ");

export function initEmoji() {
  emojis.forEach(ch => {
    const b = el("button", "", ch);
    b.onclick = () => {
      input.value += ch;
      syncComposer();
      input.focus();
    };
    $("emojiPanel").append(b);
  });
  $("emojiBtn").onclick = () => { $("emojiPanel").hidden = !$("emojiPanel").hidden; };
}
