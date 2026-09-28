# Lunex

**Inspired by ComputerCraft**  
Lunex is a programming and automation mod that transforms your Minecraft world into a fully scriptable sandbox.

---

### Key Features

**Scripting & Automation (Powered by LuaJ)**  
Harness the robust LuaJ engine. Write custom Lua scripts to automate machines and bring your code to life in the game.  
Lua natively supports JavaScript-style DOM syntax for seamless web control.

**Custom Mob Engine**  
Build unique creatures from the ground up.  
Use a skill-tree style Trait system — insert materials to unlock deeper traits — and script complex AI behaviors with a combination of JSON and Lua.  
The system is event-driven / subscription-based, so you don’t need to write loops for most tasks.

**Advanced Logistics & Storage**  
Construct modular multi-block mainframe storages to manage items, fluids, and data.  
Orchestrate complex logistics networks across your entire base.

**In-Game Web Rendering (HTML & CSS)**  
Design interactive UIs with standard HTML and CSS, and render them directly in-game.  
- Works with regular screens, handheld screens, and AR glasses  
- Handheld screens can be upgraded into smartphone-like devices  
- AR glasses support overlay-style information display (similar to Wila / Neat)  
- CSS animations are fully supported  
- No Chromium embedded — the entire mod stays around **14MB**

**Advanced External Integration**  
Native support for HTTP requests and WebSocket connections.  
Parse JSON and TXT files directly in-game for real-time data synchronization with external APIs and servers.

**Developer-Friendly Environment**  
- Built-in Monaco editor with code completion, saving, and integrated API documentation  
- Templates included for quick start  
- Everything runs asynchronously by default (virtual threads + Lua coroutines)

**High Customizability**  
- Blocks can change appearance freely (chameleon system)  
- Full GeckoLib support — Blockbench models work out of the box  
- Default textures are also fully supported

---

### Design Philosophy

Lunex is built around **Lua as the core**.  
The name comes from **Lua + Nexus / Next**.

- No forced polling — only automatic block entity sync and event-driven messaging  
- Loop-heavy code is discouraged by design (though still possible)  
- Server load is carefully considered from the ground up

---

### Important Notes

**Contains AI-generated code**  
AI tools were used as a coding assistant to help implement and optimize parts of the mod.  
The core ideas, design, and overall architecture are original. All code has been carefully reviewed and tested.

**Contains external system interactions**  
This mod provides tools that can interact with external networks and execute custom scripts when initiated by the player:
- Network connections via HTTP and WebSockets (only when triggered by user scripts)
- Dynamic creation, editing, and execution of Lua, HTML/CSS, JSON, and TXT files inside the game

Please use these features responsibly.

---

Connect every element of your world.  
Unleash your coding creativity.  
Build the ultimate automated nexus.
