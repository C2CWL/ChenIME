--[[
    Cheap per-key timings for the Rime pipeline, written to context properties
    that Kotlin reads only when lag diagnostics is on.

    Mounted twice:
      lua_filter@*chen_timing@chen_xlt   -- first filter: time spent in translators
      lua_filter@*chen_timing@chen_pipe  -- last Lua filter: translators + Lua filters

    Properties (microseconds, never contains typed text):
      _chen_xlt_us, _chen_pipe_us
    Updated on each yielded candidate so a consumer that stops at the first page
    still gets a reading for the work actually done.
--]]

local M = {}

local function record(env, elapsed)
    local us = math.floor(elapsed * 1e6 + 0.5)
    if us < 0 then us = 0 end
    local ns = env.name_space or ""
    local key = (ns:find("xlt", 1, true) and "_chen_xlt_us") or "_chen_pipe_us"
    env.engine.context:set_property(key, tostring(us))
end

function M.func(input, env)
    local t0 = os.clock()
    record(env, 0)
    for cand in input:iter() do
        record(env, os.clock() - t0)
        yield(cand)
    end
end

return M
