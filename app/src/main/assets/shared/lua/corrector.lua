--[[
	错音错字提示，并标记用户词（*）与用户句子（∞）。
	示例：「给予」的正确读音是 ji yu，当用户输入 gei yu 时，在候选项的 comment 显示正确读音
	示例：「按耐」的正确写法是「按捺」，当用户输入「按耐」时，在候选项的 comment 显示正确写法

	过去依赖 translator/spelling_hints 把全拼写进 comment，再按 ［拼音］ 查找纠错表。
	那会在每个按键、每个候选上做一次反查，代价大约十几毫秒，而且提示完还把拼音清掉。
	现在优先用 cand.preedit / 当前编码查表（不区分空格），只有 comment 里已经带着 ［］ 时才走旧路径。
	结果与原先一致：匹配到的词显示纠错注释，其它候选的拼音注释仍然清空（keep_comments 未开启时）。

	容错词在 cn_dicts/corrections.dict.yaml 中，有新增建议可以提个 issue
--]]

local M = {}

local corrections_file = "/cn_dicts/corrections.dict.yaml"

local function compact_code(code)
    return (code:gsub("[ ']", ""))
end

local function add_item(corrections, code, item)
    local items = corrections[code]
    if items then
        items[#items + 1] = item
    else
        corrections[code] = { item }
    end
end

local function load_corrections()
    local corrections = {}
    local file = io.open(rime_api.get_user_data_dir() .. corrections_file, "r")
    if not file and rime_api.get_shared_data_dir then
        file = io.open(rime_api.get_shared_data_dir() .. corrections_file, "r")
    end
    if not file then
        return corrections
    end

    for line in file:lines() do
        local text, code, comment = line:match("^([^\t#][^\t]*)\t([^\t]+)\t[^\t]*\t(.+)$")
        if text and code and comment then
            local item = { text = text, comment = comment }
            add_item(corrections, code, item)
            local compact = compact_code(code)
            if compact ~= code then
                add_item(corrections, compact, item)
            end
        end
    end

    file:close()
    return corrections
end

function M.init(env)
    local config = env.engine.schema.config
    env.keep_comment = config:get_bool('translator/keep_comments')
    local delimiter = config:get_string('speller/delimiter')
    if delimiter and #delimiter > 0 and delimiter:sub(1,1) ~= ' ' then
        env.delimiter = delimiter:sub(1,1)
    end
    env.name_space = env.name_space:gsub('^*', '')
    M.style = config:get_string(env.name_space) or '{comment}'
    -- Loaded once per session and kept on the module; do not reopen the dict per key.
    if not M.corrections then
        M.corrections = load_corrections()
    end
end

local function lookup_items(pinyin, delimiter)
    if not pinyin or #pinyin == 0 then
        return nil
    end
    if delimiter then
        pinyin = pinyin:gsub(delimiter, ' ')
    end
    return M.corrections[pinyin] or M.corrections[compact_code(pinyin)]
end

local function correction_comment_for(cand, items)
    if not items then
        return nil
    end
    for i = #items, 1, -1 do
        local c = items[i]
        if cand.text == c.text then
            return c.comment
        end
    end
    return nil
end

-- Returns (correction_comment, hinted_pinyin). hinted_pinyin is only set when
-- the candidate already carried a ［拼音］ comment (the old spelling_hints path).
local function find_correction(cand, env)
    local pinyin = cand.comment:match("^［(.-)］$")
    if pinyin and #pinyin > 0 then
        return correction_comment_for(cand, lookup_items(pinyin, env.delimiter)), pinyin
    end
    local items = lookup_items(cand.preedit, env.delimiter)
        or lookup_items(env.engine.context.input, env.delimiter)
    return correction_comment_for(cand, items), nil
end

function M.func(input, env)
    for cand in input:iter() do
        -- Same observable marks as the old is_in_user_dict filter, applied first
        -- so a user-phrase comment "*" is not overwritten by a correction hint.
        if cand.type == "user_phrase" then
            cand:get_genuine().comment = "*"
        elseif cand.type == "sentence" then
            cand:get_genuine().comment = "∞"
        else
            local correction_comment, hinted_pinyin = find_correction(cand, env)
            if correction_comment then
                cand:get_genuine().comment = string.gsub(M.style, "{comment}", correction_comment)
            elseif hinted_pinyin then
                if env.keep_comment then
                    cand:get_genuine().comment = string.gsub(M.style, "{comment}", hinted_pinyin)
                else
                    cand:get_genuine().comment = ""
                end
            end
        end
        yield(cand)
    end
end

return M
