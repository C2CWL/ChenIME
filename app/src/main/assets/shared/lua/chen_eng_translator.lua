-- Normalize lookup only, preserving the original input and candidate casing.
-- Using one lowercase code also lets differently cased input share English learning.
local translator = {}

function translator.init(env)
    env.translator = Component.Translator(env.engine, "", "table_translator@translator")
end

function translator.func(input, segment, env)
    local translation = env.translator:query(input:lower(), segment)
    if not translation then return end
    for candidate in translation:iter() do
        candidate.preedit = input
        yield(candidate)
    end
end

return translator
