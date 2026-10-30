local filter = dofile('app/src/main/assets/shared/lua/chen_custom_phrase_pos.lua')
local tests = 0
local function candidate(text, n, options)
    options = options or {}
    local c = {text = text, type = options.type or (n and 'user_table' or 'normal'),
        start = options.start or 0, _end = options.finish or 3}
    function c:get_genuine() return self end
    function c:to_phrase()
        return {lang_name = options.dictionary or 'custom_phrase',
            entry = {commit_count = n, custom_code = options.code or 'zmy '}}
    end
    return c
end
local function check(candidates, expected, raw, start)
    local consumed, output = 0, {}
    local env = {engine = {schema = {config = {get_string = function() return 'custom_phrase' end}},
        context = {input = raw or 'zmy', composition = {back = function()
            return {start = start or 0, _end = #(raw or 'zmy')}
        end}}}}
    filter.init(env)
    local stream = {iter = function(self)
        return function(state)
            assert(state == self, 'iterator state must be passed')
            consumed = consumed + 1
            return candidates[consumed]
        end, self
    end}
    yield = function(c)
        output[#output + 1] = c.text
        -- Without custom phrases, each candidate must be emitted immediately.
        if expected == 'stream' then assert(consumed == #output) end
    end
    filter.func(stream, env)
    filter.fini(env)
    assert(env.dictionary == nil)
    if expected ~= 'stream' then assert(table.concat(output, ',') == expected, table.concat(output, ',')) end
    tests = tests + 1
end
local a, b, c = candidate('怎么样'), candidate('在没有'), candidate('normal3')
check({candidate('邹明妤', 2), a, b}, '怎么样,邹明妤,在没有')
check({candidate('x', 0), a}, 'x,怎么样')
check({candidate('x', -5), a}, 'x,怎么样')
check({candidate('x', 2147483647), a}, '怎么样,x')
check({candidate('x', 100), a, b}, '怎么样,在没有,x')
check({candidate('x', 2), candidate('y', 2), a, b, c}, '怎么样,x,y,在没有,normal3')
check({candidate('x', 4), candidate('y', 1), a, b, c}, 'y,怎么样,在没有,x,normal3')
check({candidate('x', 3), candidate('y', 3)}, 'x,y')
check({a, b, c}, 'stream')
check({}, '')
check({candidate('pinned', nil, {type = 'chen_pinned'}), candidate('x', 1), a}, 'pinned,x,怎么样')
for _, options in ipairs({{dictionary = 'cn_en'}, {type = 'user_phrase'}, {code = 'zm '}, {start = 1}, {finish = 2}}) do
    check({candidate('excluded', 2, options), a}, 'excluded,怎么样')
end
check({candidate('excluded', 2), a}, 'excluded,怎么样', 'zmyx')
check({candidate('excluded', 2), a}, 'excluded,怎么样', 'zm')
check({candidate('x', 2, {start = 3, finish = 6}), a, b}, '怎么样,x,在没有', '你zmy', 3)
local many = {}
for i = 1, 10000 do many[i] = candidate(tostring(i)) end
check(many, 'stream')
-- Exercise the actual pin filter and its ShadowCandidate marker.
local pin = dofile('app/src/main/assets/shared/lua/pin_cand_filter.lua')
ShadowCandidate = function(c, kind, text, comment)
    return setmetatable({type = kind, text = text, comment = comment}, {__index = c})
end
local candidates = {candidate('custom', 2), candidate('pin'), a}
for _, item in ipairs(candidates) do item.preedit = 'zmy' end
local index, pinned = 0, {}
yield = function(c) pinned[#pinned + 1] = c end
pin.func({iter = function(self)
    return function(state)
        assert(state == self)
        index = index + 1
        return candidates[index]
    end, self
end}, {pin_cands = {zmy = {'pin'}}, engine = {context = {
    get_option = function() return true end,
    get_preedit = function() return {text = 'zmy'} end,
}}})
assert(pinned[1].type == 'chen_pinned')
check(pinned, 'pin,custom,怎么样')
print(('PASS: %d filter tests'):format(tests))
