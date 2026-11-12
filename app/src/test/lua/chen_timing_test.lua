local tests = 0
local function assert_true(cond, msg)
    if not cond then error(msg or 'assert_true', 2) end
    tests = tests + 1
end

local M = dofile('app/src/main/assets/shared/lua/chen_timing.lua')

local function make_env(ns)
    local props = {}
    return {
        name_space = ns,
        engine = {
            context = {
                props = props,
                set_property = function(self, k, v) self.props[k] = v end,
            },
        },
    }, props
end

local function make_stream(n)
    local consumed = 0
    local cands = {}
    for i = 1, n do cands[i] = { text = tostring(i) } end
    return {iter = function(self)
        return function(state)
            assert(state == self)
            consumed = consumed + 1
            return cands[consumed]
        end, self
    end}, function() return consumed end
end

local env, props = make_env('chen_xlt')
local stream, consumed_of = make_stream(8)
local yielded = 0
yield = function(c)
    yielded = yielded + 1
    if yielded >= 5 then error('STOP', 0) end
end
local ok, err = pcall(M.func, stream, env)
if not ok and err ~= 'STOP' then error(err) end
assert_true(props._chen_xlt_us ~= nil, 'xlt property is set')
assert_true(tonumber(props._chen_xlt_us) ~= nil, 'xlt is a number')
assert_true(tonumber(props._chen_xlt_us) >= 0, 'xlt is non-negative')
assert_true(props._chen_pipe_us == nil, 'xlt role does not write pipe')
assert_true(consumed_of() == 5, 'timing filter is streaming')

env, props = make_env('chen_pipe')
stream, consumed_of = make_stream(3)
yielded = 0
yield = function(c) yielded = yielded + 1 end
M.func(stream, env)
assert_true(props._chen_pipe_us ~= nil, 'pipe property is set')
assert_true(tonumber(props._chen_pipe_us) ~= nil, 'pipe is a number')
assert_true(props._chen_xlt_us == nil, 'pipe role does not write xlt')
assert_true(yielded == 3 and consumed_of() <= 4, 'pipe yields every candidate')

-- Empty iterator still records a zero, so Kotlin does not read a stale value.
env, props = make_env('chen_xlt')
stream = {iter = function(self) return function() return nil end, self end}
yield = function() error('should not yield') end
M.func(stream, env)
assert_true(props._chen_xlt_us == '0', 'empty input records zero')

print(('PASS: %d chen_timing tests'):format(tests))
