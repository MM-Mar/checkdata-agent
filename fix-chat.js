const fs = require('fs');
let src = fs.readFileSync('h:/code/checkdata-agent/src/main/resources/static/js/chat.js', 'utf8');

// Fix suggestions array
const fixedSuggestions = `        const suggestions = [
            { text: '你有哪些技能？' },
            { text: '帮我探索数据库表结构' },
            { text: '分析销售数据趋势' },
            { text: '生成一份可视化图表' }
        ];`;

src = src.replace(/\s*const suggestions = \[[\s\S]*?\n\s*\];/, fixedSuggestions);

// Now repeatedly check syntax and find remaining errors
function checkSyntax(code) {
    try {
        new Function(code);
        return { ok: true };
    } catch (e) {
        const m = e.message.match(/:(\d+):(\d+)/);
        return { ok: false, line: m ? parseInt(m[1]) : null, msg: e.message };
    }
}

function findBrokenStrings(code) {
    const lines = code.split('\n');
    const results = [];
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        // Find lines with high-byte chars inside single/double quotes that look broken
        // Pattern: string with non-ASCII + possible broken quote
        const strMatches = line.match(/['"][^'"]*[\u4e00-\u9fff\uFFFD][^'"]*['"]/g);
        if (strMatches) {
            strMatches.forEach(m => {
                if (m.length < 4 || m.includes('\uFFFD')) return;
                // Check if the string content is valid by trying to parse just this line
                try {
                    new Function('var x = ' + m);
                } catch {
                    results.push({ line: i + 1, content: line.trim().substring(0, 120), match: m });
                }
            });
        }
    }
    return results;
}

const result = checkSyntax(src);
if (result.ok) {
    console.log('SYNTAX OK after suggestions fix');
} else {
    console.log('Still ERR at line', result.line, ':', result.msg);
    if (result.line) {
        const lines = src.split('\n');
        for (let i = Math.max(0, result.line - 5); i < Math.min(lines.length, result.line + 5); i++) {
            console.log((i + 1) + ': ' + lines[i].substring(0, 150));
        }
    }
}

fs.writeFileSync('h:/code/checkdata-agent/src/main/resources/static/js/chat.js', src, 'utf8');
console.log('Written');