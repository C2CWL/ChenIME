local tests = 0
local function assert_eq(actual, expected, msg)
    if actual ~= expected then
        error((msg or 'assert_eq') .. ': expected [' .. tostring(expected) .. '] got [' .. tostring(actual) .. ']', 2)
    end
    tests = tests + 1
end

rime_api = {
    get_user_data_dir = function() return '/tmp/chen_corrector_missing' end,
    get_shared_data_dir = function() return '/tmp/chen_corrector_missing' end,
}

local M = dofile('app/src/main/assets/shared/lua/corrector.lua')

local function make_env(opts)
    opts = opts or {}
    return {
        engine = {
            schema = {
                config = {
                    get_bool = function(_, _) return opts.keep_comment and true or nil end,
                    get_string = function(_, key)
                        if key == 'speller/delimiter' then return opts.delimiter or ' ' end
                        return opts.style or '{comment}'
                    end,
                },
            },
            context = { input = opts.input or '' },
        },
        name_space = 'corrector',
    }
end

local function make_cand(text, comment, ctype, preedit)
    return {
        text = text,
        comment = comment or '',
        type = ctype or 'phrase',
        preedit = preedit or '',
        get_genuine = function(self) return self end,
    }
end

local function run(env, cands, stop_after)
    M.init(env)
    M.corrections = {
        ['hun dun'] = { { text = '馄饨', comment = '馄(hun)饨(tun)' } },
        ['hundun'] = { { text = '馄饨', comment = '馄(hun)饨(tun)' } },
        ['gei yu'] = { { text = '给予', comment = 'jǐ yǔ' } },
        ['geiyu'] = { { text = '给予', comment = 'jǐ yǔ' } },
    }
    local consumed, output = 0, {}
    local stream = {iter = function(self)
        return function(state)
            assert(state == self)
            consumed = consumed + 1
            return cands[consumed]
        end, self
    end}
    yield = function(c)
        output[#output + 1] = c
        if stop_after and #output >= stop_after then
            error('STOP', 0)
        end
    end
    local ok, err = pcall(M.func, stream, env)
    if not ok and err ~= 'STOP' then error(err) end
    return output, consumed
end

-- Old path: ［pinyin］ comment still works, and unmatched comments are cleared.
local env = make_env()
local out = run(env, {
    make_cand('馄饨', '［hun dun］'),
    make_cand('混沌', '［hun dun］'),
    make_cand('你好', '［ni hao］'),
})
assert_eq(out[1].comment, '馄(hun)饨(tun)', 'hinted wonton')
assert_eq(out[2].comment, '', 'other word sharing the pinyin is cleared')
assert_eq(out[3].comment, '', 'unrelated pinyin is cleared')

-- keep_comments preserves the pinyin when there is no correction.
env = make_env({ keep_comment = true })
out = run(env, { make_cand('你好', '［ni hao］') })
assert_eq(out[1].comment, 'ni hao', 'keep_comments')

-- New path: no spelling_hints comment, look up by preedit (with spaces).
env = make_env()
out = run(env, {
    make_cand('馄饨', '', 'phrase', 'hun dun'),
    make_cand('混沌', '', 'phrase', 'hun dun'),
})
assert_eq(out[1].comment, '馄(hun)饨(tun)', 'preedit spaced')
assert_eq(out[2].comment, '', 'preedit other text stays empty')

-- New path: compact preedit and compact input both match a spaced table key.
env = make_env({ input = 'hundun' })
out = run(env, { make_cand('馄饨', '', 'phrase', 'hundun') })
assert_eq(out[1].comment, '馄(hun)饨(tun)', 'compact preedit')

env = make_env({ input = 'geiyu' })
out = run(env, { make_cand('给予', '', 'phrase', '') })
assert_eq(out[1].comment, 'jǐ yǔ', 'compact input')

-- A candidate whose text is not in the table is left alone when there is no ［］ comment.
env = make_env({ input = 'hundun' })
out = run(env, { make_cand('混沌', '', 'phrase', 'hundun') })
assert_eq(out[1].comment, '', 'no false hint')

-- User-phrase / sentence marks (formerly is_in_user_dict), and they win over hints.
env = make_env({ input = 'hundun' })
out = run(env, {
    make_cand('馄饨', '［hun dun］', 'user_phrase', 'hun dun'),
    make_cand('馄饨馄饨', '', 'sentence', 'hun dun'),
    make_cand('馄饨', '', 'phrase', 'hun dun'),
})
assert_eq(out[1].comment, '*', 'user_phrase star')
assert_eq(out[2].comment, '∞', 'sentence mark')
assert_eq(out[3].comment, '馄(hun)饨(tun)', 'regular still hinted')

-- Streaming: only pull the first page, the rest of the dict is not iterated.
local many = {}
for i = 1, 10000 do
    many[i] = make_cand('词' .. i, '［ni hao］')
end
out, consumed = run(make_env(), many, 16)
assert_eq(#out, 16, 'stopped after a page')
assert_eq(consumed, 16, 'did not drain the rest of the iterator')

print(('PASS: %d corrector tests'):format(tests))
