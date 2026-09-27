local _sys = system
if not _sys then
	return
end

local unpack_fn = table.unpack or unpack
local _listeners = {}
local _tasks = {}

function addEventListener(eventName, callbackOrName)
	if type(callbackOrName) ~= "string" and type(callbackOrName) ~= "function" then
		error("addEventListener requires a string function name (e.g. 'onClick') or a function")
	end

	if not _listeners[eventName] then
		_listeners[eventName] = {}
	end

	for _, cb in ipairs(_listeners[eventName]) do
		if cb == callbackOrName then
			return
		end
	end
	table.insert(_listeners[eventName], callbackOrName)
end

function removeEventListener(eventName, callbackOrName)
	if _listeners[eventName] then
		for i, cb in ipairs(_listeners[eventName]) do
			if cb == callbackOrName then
				table.remove(_listeners[eventName], i)
				break
			end
		end
	end
end

system = setmetatable({}, {
	__index = function(t, k)
		if k == "on" then
			return addEventListener
		elseif k == "off" then
			return removeEventListener
		elseif k == "awaitEvent" then
			return function(targetEvent, timeout)
				local startTime = tonumber(_sys.getTime()) or 0
				while true do
					local ev = coroutine.yield("awaitEvent")
					if ev and ev.name == targetEvent then
						local args = {}
						if ev.args then
							for i = 1, #ev.args do
								args[i] = ev.args[i]
							end
						end
						return unpack_fn(args)
					end
					if timeout then
						local currentTime = tonumber(_sys.getTime()) or 0
						if (currentTime - startTime) >= timeout then
							return nil, "timeout"
						end
					end
				end
			end
		elseif k == "sleep" then
			return function(millis)
				local safeMillis = tonumber(millis) or 0
				local currentTime = tonumber(_sys.getTime()) or 0
				local wakeup = currentTime + safeMillis
				while (tonumber(_sys.getTime()) or 0) < wakeup do
					coroutine.yield("sleep")
				end
			end
		elseif k == "createState" then
			return function(initial_table)
				local _data = initial_table or {}
				local _proxy = {}
				local _callbacks = {}

				setmetatable(_proxy, {
					__index = function(pt, pk)
						if pk == "onChange" then
							return function(key, cb)
								if not _callbacks[key] then
									_callbacks[key] = {}
								end
								table.insert(_callbacks[key], cb)
							end
						elseif pk == "bind" then
							return function(key, getter, interval)
								table.insert(_tasks, coroutine.create(function()
									while true do
										_proxy[key] = getter()
										system.sleep(interval or 50)
									end
								end))
							end
						end
						return _data[pk]
					end,
					__newindex = function(pt, pk, pv)
						local old = _data[pk]
						if old ~= pv then
							_data[pk] = pv
							if _callbacks[pk] then
								for _, cb in ipairs(_callbacks[pk]) do
									table.insert(_tasks, coroutine.create(function()
										if type(cb) == "string" and _G[cb] then
											_G[cb](pv, old)
										elseif type(cb) == "function" then
											cb(pv, old)
										end
									end))
								end
							end
						end
					end
				})
				return _proxy
			end
		end
		return _sys[k]
	end
})

function system.waitForChange(getter, timeout)
	local start_time = tonumber(_sys.getTime()) or 0
	local initial_val = getter()
	while true do
		system.sleep(50)
		local current_val = getter()
		if current_val ~= initial_val then
			return current_val, initial_val
		end
		if timeout then
			local current_time = tonumber(_sys.getTime()) or 0
			if (current_time - start_time) >= timeout then
				return nil, "timeout"
			end
		end
	end
end

function system.waitUntil(condition, timeout)
	local start_time = tonumber(_sys.getTime()) or 0
	while true do
		system.sleep(50)
		if condition() then
			return true
		end
		if timeout then
			local current_time = tonumber(_sys.getTime()) or 0
			if (current_time - start_time) >= timeout then
				return nil, "timeout"
			end
		end
	end
end

parallel = {}
function parallel.waitForAll(...)
	local funcs = {...}
	local coroutines = {}
	for i, f in ipairs(funcs) do
		local co = coroutine.create(f)
		table.insert(coroutines, co)
		table.insert(_tasks, co)
	end
	while true do
		local all_dead = true
		for _, co in ipairs(coroutines) do
			if coroutine.status(co) ~= "dead" then
				all_dead = false
				break
			end
		end
		if all_dead then
			break
		end
		coroutine.yield("parallel")
	end
end

function parallel.waitForAny(...)
	local funcs = {...}
	local coroutines = {}
	for i, f in ipairs(funcs) do
		local co = coroutine.create(f)
		table.insert(coroutines, co)
		table.insert(_tasks, co)
	end
	local result_index = 0
	while true do
		for i, co in ipairs(coroutines) do
			if coroutine.status(co) == "dead" then
				result_index = i
				break
			end
		end
		if result_index > 0 then
			break
		end
		coroutine.yield("parallel")
	end
	return result_index
end

package = { loaded = {} }
function require(module_path)
	if package.loaded[module_path] then
		return package.loaded[module_path]
	end
	local path = module_path
	if not string.match(path, "%.lua$") then
		path = path .. ".lua"
	end
	local code = fs.readFile(path)
	if not code or type(code) ~= "string" then
		error("module '" .. module_path .. "' not found in fs")
	end
	local chunk = system.compile(code)
	if not chunk or type(chunk) ~= "function" then
		error("failed to compile module '" .. module_path .. "'")
	end
	local res = chunk()
	if res == nil then
		res = true
	end
	package.loaded[module_path] = res
	return res
end

if http then
	local native_http = http
	http = setmetatable({}, { __index = native_http })
	function http.request(url, method, body, headers)
		local eventName = "http_response_" .. tostring(tonumber(_sys.getTime()) or 0) .. "_" .. tostring(math.random(10000, 99999))
		native_http.requestAsync(url, method, body, headers, eventName)
		local responseUrl, res = system.awaitEvent(eventName)
		return res
	end
	function http.websocket(url, headers)
		local eventName = "ws_response_" .. tostring(tonumber(_sys.getTime()) or 0) .. "_" .. tostring(math.random(10000, 99999))
		native_http.websocketAsync(url, headers, eventName)
		local responseUrl, wsObj, err = system.awaitEvent(eventName)
		if not wsObj then
			return false, err
		end

		local is_open = true
		wsObj.onMessage = nil
		wsObj.onClose = nil

		local function onCloseListener(evUrl, reason)
			if evUrl == url then
				is_open = false
				removeEventListener("websocket_closed", onCloseListener)
				if type(wsObj.onClose) == "string" and _G[wsObj.onClose] then
					_G[wsObj.onClose](reason)
				elseif type(wsObj.onClose) == "function" then
					wsObj.onClose(reason)
				end
			end
		end
		addEventListener("websocket_closed", onCloseListener)

		local function onMessageListener(evUrl, msg)
			if evUrl == url then
				if type(wsObj.onMessage) == "string" and _G[wsObj.onMessage] then
					_G[wsObj.onMessage](msg)
				elseif type(wsObj.onMessage) == "function" then
					wsObj.onMessage(msg)
				end
			end
		end
		addEventListener("websocket_message", onMessageListener)

		local native_send = wsObj.send
		wsObj.send = function(msg)
			if not is_open then
				error("Attempt to send to closed WebSocket")
			end
			native_send(msg)
		end
		local native_close = wsObj.close
		wsObj.close = function()
			if is_open then
				is_open = false
				removeEventListener("websocket_closed", onCloseListener)
				removeEventListener("websocket_message", onMessageListener)
				native_close()
			end
		end
		return wsObj
	end
end

if fs then
	local native_fs = fs
	fs = setmetatable({}, { __index = native_fs })
	function fs.saveFile(path, data)
		local content = data
		if type(data) == "table" then
			content = system.toJson(data)
		elseif type(data) ~= "string" then
			content = tostring(data)
		end
		return native_fs.saveFile(path, content)
	end
	function fs.readAsync(path)
		local eventName = "fs_read_" .. tostring(tonumber(_sys.getTime()) or 0) .. "_" .. tostring(math.random(10000, 99999))
		local success = native_fs.readFileAsync(path, eventName)
		if not success then
			return nil, "Invalid path, file not found, or is a directory"
		end
		local responsePath, data, err = system.awaitEvent(eventName)
		return data, err
	end
end

if mcNet then
	local native_mcNet = mcNet
	mcNet = setmetatable({}, { __index = native_mcNet })
	function mcNet.onMessage(callback) addEventListener("mcnet_receive", callback)
	end
	function mcNet.onWanMessage(callback) addEventListener("mcnet_wan_receive", callback)
	end
	function mcNet.removeListener(callback)
		removeEventListener("mcnet_receive", callback)
		removeEventListener("mcnet_wan_receive", callback)
	end
end

if mcLAN then
	local native_mcLAN = mcLAN
	mcLAN = setmetatable({}, { __index = native_mcLAN })
	function mcLAN.onMessage(callback) addEventListener("mclan_message", callback)
	end
	function mcLAN.removeListener(callback) removeEventListener("mclan_message", callback)
	end
end

function system.setInterval(callbackOrName, interval)
	local eventName = "timer_interval_" .. tostring(tonumber(_sys.getTime()) or 0) .. "_" .. tostring(math.random(10000, 99999))
	local timerId = _sys.startTimer(interval or 1000, eventName)
	addEventListener(eventName, callbackOrName)
	return { id = timerId, event = eventName, cb = callbackOrName }
end

function system.clearInterval(timerObj)
	if type(timerObj) == "table" and timerObj.id then
		_sys.stopTimer(timerObj.id)
		removeEventListener(timerObj.event, timerObj.cb)
	end
end

function system.setTimeout(callbackOrName, delay)
	local eventName = "timer_timeout_" .. tostring(tonumber(_sys.getTime()) or 0) .. "_" .. tostring(math.random(10000, 99999))
	local timerId = _sys.startTimeout(delay or 1000, eventName)
	local function wrapper(...)
		removeEventListener(eventName, wrapper)
		if type(callbackOrName) == "string" and _G[callbackOrName] then
			_G[callbackOrName](...)
		elseif type(callbackOrName) == "function" then
			callbackOrName(...)
		end
	end
	addEventListener(eventName, wrapper)
	return { id = timerId, event = eventName, cb = wrapper }
end

function system.clearTimeout(timerObj)
	system.clearInterval(timerObj)
end

-- ========================================================
-- メインスケジューラ・ループ
-- ========================================================
function sys_runScheduler(mainChunk)
	local mainTask = coroutine.create(mainChunk)
	table.insert(_tasks, mainTask)
	local onInitStarted = false

	while sys_isRunning() do
		if not onInitStarted and coroutine.status(mainTask) == 'dead' then
			if _G["on_init"] and type(_G["on_init"]) == "function" then
				table.insert(_tasks, coroutine.create(_G["on_init"]))
			end
			onInitStarted = true
		end

		local hasEvent = false
		local ev = sys_pollEvent(30)

		if ev then
			hasEvent = true
			local safeArgs = {}
			if ev.args then
				for idx = 1, #ev.args do
					safeArgs[idx] = ev.args[idx]
				end
			end

			if _listeners[ev.name] then
				for _, cb in ipairs(_listeners[ev.name]) do
					if type(cb) == "string" then
						if _G[cb] and type(_G[cb]) == "function" then
							table.insert(_tasks, coroutine.create(function() _G[cb](unpack_fn(safeArgs)) end))
						end
					elseif type(cb) == "function" then
						table.insert(_tasks, coroutine.create(function() cb(unpack_fn(safeArgs)) end))
					end
				end
			end

			if _G[ev.name] and type(_G[ev.name]) == "function" then
				table.insert(_tasks, coroutine.create(function() _G[ev.name](unpack_fn(safeArgs)) end))
			end

			local i = 1
			while i <= #_tasks do
				local task = _tasks[i]
				if coroutine.status(task) ~= 'dead' then
					local ok, err = coroutine.resume(task, ev)
					if not ok then
						error(err)
					end
				end
				if coroutine.status(task) == 'dead' then
					table.remove(_tasks, i)
				else
					i = i + 1
				end
			end
		end

		if not hasEvent then
			local i = 1
			while i <= #_tasks do
				local task = _tasks[i]
				if coroutine.status(task) ~= 'dead' then
					local ok, err = coroutine.resume(task)
					if not ok then
						error(err)
					end
				end
				if coroutine.status(task) == 'dead' then
					table.remove(_tasks, i)
				else
					i = i + 1
				end
			end
		end
	end
end