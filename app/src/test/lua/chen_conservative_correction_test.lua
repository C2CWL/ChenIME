local filter = dofile('app/src/main/assets/shared/lua/chen_conservative_correction.lua')
local tests = 0
local function check(types, expected)
    local consumed, output = 0, {}
    local stream = {iter = function(self)
        return function(state)
            assert(state == self)
            consumed = consumed + 1
            if types[consumed] then return {type = types[consumed], text = tostring(consumed)} end
        end, self
    end}
    yield = function(c)
        output[#output + 1] = c.text
        if types[1] ~= 'chen_correction' then assert(consumed == #output) end
    end
    filter.func(stream)
    assert(table.concat(output, ',') == expected, table.concat(output, ','))
    tests = tests + 1
end
check({}, '')
check({'phrase', 'phrase'}, '1,2')
check({'chen_correction', 'phrase', 'phrase'}, '2,1,3')
check({'chen_correction', 'chen_correction', 'phrase'}, '3,1,2')
check({'chen_correction', 'user_table', 'phrase'}, '2,1,3')
check({'user_table', 'chen_correction', 'phrase'}, '1,2,3')
check({'phrase', 'chen_correction', 'phrase'}, '1,2,3')
check({'chen_correction', 'chen_correction'}, '1,2')
check({'chen_correction', 'completion'}, '2,1')
local many = {}
for i = 1, 10000 do many[i] = 'phrase' end
local expected = {}
for i = 1, #many do expected[i] = tostring(i) end
check(many, table.concat(expected, ','))
print(('PASS: %d conservative correction tests'):format(tests))
