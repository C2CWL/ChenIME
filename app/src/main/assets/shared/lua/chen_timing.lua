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

    Writes nothing unless the context property _chen_lag_diag is "1". Kotlin sets
    that only while 卡顿诊断 is on, so a normal keystroke does not call
    set_property once per candidate.
--]]

local M = {}

local function enabled(env)
    local ctx = env.engine.context
    -- Same switch as Kotlin LagDiagnostics / InputLatency. Absent or anything
    -- other than "1" means diagnostics are off: do not touch context properties.
    return ctx:get_property("_chen_lag_diag") == "1"
end

local function record(env, elapsed)
    if not enabled(env) then return end
    local us = math.floor(elapsed * 1e6 + 0.5)
    if us < 0 then us = 0 end
    local ns = env.name_space or ""
    local key = (ns:find("xlt", 1, true) and "_chen_xlt_us") or "_chen_pipe_us"
    env.engine.context:set_property(key, tostring(us))
end

function M.func(input, env)
    local on = enabled(env)
    local t0 = on and os.clock() or nil
    if on then record(env, 0) end
    for cand in input:iter() do
        if on then record(env, os.clock() - t0) end
        yield(cand)
    end
end

return M
