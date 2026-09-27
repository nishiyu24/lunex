// Import the long code from the separate file created earlier
import {modalUiCode} from './templates/template_modal.js';

// Template data definition
export const Templates = {
    // Default code for when creating a new file, etc.
    defaultCode: `-- Lunex Custom Editor\nfunction on_init()\n    system.chat("Hello World!")\nend\n`,

    // List of templates available from the dropdown
    list: [
        {
            id: "basic",
            name: "Basic Structure (Hello World)",
            code: `-- Basic program\nfunction on_init()\n    -- Write the initialization logic here\n    system.chat("Hello!")\nend\n`
        },
        {
            id: "loop",
            name: "Loop Processing",
            code: `-- Repeat every second\nfunction on_init()\n    for i = 1, 5 do\n        system.chat("Iteration: " .. i)\n        system.sleep(20) -- 20 ticks = 1 second\n    end\nend\n`
        },
        {
            id: "event",
            name: "Event Handling",
            code: `-- React when clicked\nfunction on_click()\n    system.chat("Clicked!")\nend\n`
        },
        {
            id: "modal_ui",
            name: "Modal UI Control (HTML/CSS Supported)",
            // Assign the imported variable (containing the long code) here
            code: modalUiCode
        }
    ]
};