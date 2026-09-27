export const modalUiCode = `

system.chat("=== Window Test ===")

local myScreen = device.get(Direction.LEFT, DEVICE.SCREEN)

if myScreen then

    local linked = myScreen.linkSpeakers({Direction.UP})

    if linked then
        system.chat("Connected to speaker")
    else
        system.chat("Speaker not found")
    end

    local htmlContent = fs.readFile("./web/video.html")

    if htmlContent then
        myScreen.load(htmlContent)
    else
        system.chat("File not found (404)")
        return
    end

else

    system.chat("Screen not found")

end



-- ========================================================
-- [HTML/CSS Template]
-- You can design the UI using HTML and CSS as shown below.
-- If needed, uncomment the code below and save it as an HTML file to use it.
-- ========================================================
--[[
<!DOCTYPE html>
<html lang="en">
<head>
    <style>
        body {
            margin: 0;
            background-color: #111;
            color: #fff;
            font-family: sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
        }

        .movie-screen {
            width: 480px;
            height: 270px;
            border: 4px solid #fff;
            border-radius: 8px;
            margin-top: 20px;
        }

        .control-panel {
            margin-top: 15px;
            display: flex;
            gap: 10px;
        }

        button {
            background: #333;
            color: #fff;
            border: 2px solid #555;
            padding: 8px 16px;
            font-weight: bold;
            border-radius: 6px;
            cursor: pointer;
        }

        button:hover {
            background: #555;
        }

        button:active {
            background: #222;
            border-color: #888;
        }
    </style>
</head>
<body>
<!-- ★ Specify the YouTube or Google Drive shared link directly (auto-converted on the Java side) -->
<video class="movie-screen" id="my_video"
       src="https://youtu.be/6e0EnWx2o8g"></video>

<!-- Playback / Speed Controls -->
<div class="control-panel">
    <button id="btn_pause">⏯ Play / Pause</button>
    <button id="btn_speed_05">x0.5</button>
    <button id="btn_speed_10">x1.0</button>
    <button id="btn_speed_20">x2.0</button>
</div>

<!-- Seek (Time Skip) Controls -->
<div class="control-panel">
    <button id="btn_seek_0">⏮ From Beginning</button>
    <button id="btn_seek_30">Go to 0:30</button>
    <button id="btn_seek_60">Go to 1:00</button>
</div>

<!-- ★ Client-side Lua script starts here -->
<script type="text/lua">
    local btnPause = document.getElementById("btn_pause")
    local btnSpeed05 = document.getElementById("btn_speed_05")
    local btnSpeed10 = document.getElementById("btn_speed_10")
    local btnSpeed20 = document.getElementById("btn_speed_20")
    local btnSeek0 = document.getElementById("btn_seek_0")
    local btnSeek30 = document.getElementById("btn_seek_30")
    local btnSeek60 = document.getElementById("btn_seek_60")

    -- Get the video as a DOM element
    local myVideo = document.getElementById("my_video")
    local currentSpeed = 1.0

    btnPause.addEventListener("onclick", function()
        if currentSpeed > 0 then
            currentSpeed = 0.0
        else
            currentSpeed = 1.0
        end
        myVideo.setSpeed(currentSpeed)
    end)

    btnSpeed05.addEventListener("onclick", function()
        currentSpeed = 0.5
        myVideo.setSpeed(currentSpeed)
    end)

    btnSpeed10.addEventListener("onclick", function()
        currentSpeed = 1.0
        myVideo.setSpeed(currentSpeed)
    end)

    btnSpeed20.addEventListener("onclick", function()
        currentSpeed = 2.0
        myVideo.setSpeed(currentSpeed)
    end)

    btnSeek0.addEventListener("onclick", function() myVideo.seek(0.0) end)
    btnSeek30.addEventListener("onclick", function() myVideo.seek(30.0) end)
    btnSeek60.addEventListener("onclick", function() myVideo.seek(60.0) end)
</script>
</body>
</html>
]]`;