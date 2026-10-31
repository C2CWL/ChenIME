-- Normalize lookup only, preserving the original input and candidate casing.
-- Using one lowercase code also lets differently cased input share English learning.
--
-- table_translator lists completions by length (all 3-letter words before any
-- 4-letter word) and melt_eng has no weights, so common words were buried under
-- rare short ones. After exact matches and learned words, insert the most
-- frequent completions from en_dicts/chen_eng_rank.txt. Each one is fetched
-- through the same translator, so selecting it still updates the user dictionary.
local translator = {}

local rank_file = "/en_dicts/chen_eng_rank.txt"
local default_top_count = 8
-- Table completions have quality initial_quality - 1 plus a negligible weight;
-- user dictionary completions get +0.5 and their own weight on top of that.
local table_completion_quality = -0.75

-- Shared by every chen_eng session in this Lua state; read once.
local buckets

local function open_rank_file()
    local file = io.open(rime_api.get_user_data_dir() .. rank_file, "r")
    if not file and rime_api.get_shared_data_dir then
        file = io.open(rime_api.get_shared_data_dir() .. rank_file, "r")
    end
    return file
end

-- buckets[first letter] and buckets[first two letters] list words by rank.
local function load_buckets()
    if buckets then return buckets end
    buckets = {}
    local file = open_rank_file()
    if not file then return buckets end
    local function add(key, word)
        local bucket = buckets[key]
        if not bucket then
            bucket = {}
            buckets[key] = bucket
        end
        bucket[#bucket + 1] = word
    end
    for line in file:lines() do
        if line:sub(1, 1) ~= "#" then
            local text, code = line:match("^([^\t]+)\t?([^\t]*)$")
            if text then
                if code == "" then code = text end
                code = code:lower()
                local word = { text = text, code = code }
                add(code:sub(1, 1), word)
                if #code > 1 then add(code:sub(1, 2), word) end
            end
        end
    end
    file:close()
    return buckets
end

local function frequent_completions(code, count)
    local result = {}
    local bucket = load_buckets()[code:sub(1, 2)]
    if not bucket then return result end
    for _, word in ipairs(bucket) do
        if #word.code > #code and word.code:sub(1, #code) == code then
            result[#result + 1] = word
            if #result >= count then break end
        end
    end
    return result
end

-- Exact entries are listed first, so only the head of the translation is read.
local function lookup(env, word, segment)
    local translation = env.translator:query(word.code, segment)
    if not translation then return nil end
    for candidate in translation:iter() do
        if candidate.type == "completion" then return nil end
        if candidate.text == word.text then return candidate end
    end
    return nil
end

function translator.init(env)
    env.translator = Component.Translator(env.engine, "", "table_translator@translator")
    local config = env.engine.schema.config
    local name_space = env.name_space:gsub("^*", "")
    env.top_count = config:get_int(name_space .. "/frequent_completions") or default_top_count
end

function translator.func(input, segment, env)
    local code = input:lower()
    local translation = env.translator:query(code, segment)
    if not translation then return end
    local frequent = {}
    if env.top_count > 0 and code:match("^[a-z][a-z']*$") then
        frequent = frequent_completions(code, env.top_count)
    end
    local seen = {}
    local function emit(candidate)
        if seen[candidate.text] then return end
        seen[candidate.text] = true
        candidate.preedit = input
        yield(candidate)
    end
    local function emit_frequent()
        for _, word in ipairs(frequent) do
            if not seen[word.text] then
                local candidate = lookup(env, word, segment)
                if candidate then
                    candidate.comment = "~" .. word.code:sub(#code + 1)
                    emit(candidate)
                end
            end
        end
        frequent = {}
    end
    for candidate in translation:iter() do
        if #frequent > 0 and candidate.type == "completion"
            and candidate.quality < table_completion_quality then
            emit_frequent()
        end
        emit(candidate)
    end
    emit_frequent()
end

return translator
