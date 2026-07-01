const fs = require('fs');
const path = require('path');
const baseDir = 'd:\\Workspace\\ai-agent\\ai-agent-ui-design-draft\\pages';
const chatHtml = fs.readFileSync(path.join(baseDir, 'chat.html'), 'utf-8');

const sidebarStartMarker = '    <!-- Sidebar -->';
const sidebarEndMarker = '    </aside>';
const sidebarStartIdx = chatHtml.indexOf(sidebarStartMarker);
const sidebarEndIdx = chatHtml.indexOf(sidebarEndMarker, sidebarStartIdx) + sidebarEndMarker.length;
const sidebarHtmlTemplate = chatHtml.substring(sidebarStartIdx, sidebarEndIdx);

const styleBlocks = [];
let searchIdx = 0;
while (true) {
    const start = chatHtml.indexOf('<style>', searchIdx);
    if (start === -1) break;
    const end = chatHtml.indexOf('</style>', start);
    styleBlocks.push({ start, end: end + 9, content: chatHtml.substring(start, end + 9) });
    searchIdx = end + 9;
}

const secondStyleContent = styleBlocks[styleBlocks.length - 1].content;
const cssStart = secondStyleContent.indexOf('        /* Light sidebar */');
const cssEnd = secondStyleContent.indexOf('        /* Header */');
const sidebarCssTemplate = secondStyleContent.substring(cssStart, cssEnd);

console.log('Template HTML length:', sidebarHtmlTemplate.length);
console.log('Template CSS length:', sidebarCssTemplate.length);

const pages = ['dashboard.html', 'knowledge.html', 'agent.html', 'mcp.html', 'settings.html'];
const activeMap = {
    'dashboard.html': 'nav-dashboard',
    'knowledge.html': 'nav-knowledge',
    'agent.html': 'nav-agent',
    'mcp.html': 'nav-mcp',
    'settings.html': 'nav-settings'
};

for (const page of pages) {
    const filePath = path.join(baseDir, page);
    let content = fs.readFileSync(filePath, 'utf-8');
    const originalContent = content;

    let sidebarStart = -1;
    let sidebarEnd = -1;
    const patterns = [
        '    <!-- Sidebar -->',
        '    <aside class="sidebar">',
        '    <div class="sidebar">',
        '        <!-- Sidebar -->',
        '        <aside class="flex flex-col shrink-0"',
        '        <aside class="flex flex-col shrink-0 sidebar-pastel"'
    ];
    for (const pattern of patterns) {
        sidebarStart = content.indexOf(pattern);
        if (sidebarStart !== -1) break;
    }
    if (sidebarStart === -1) { console.log('SKIP ' + page + ': no sidebar start'); continue; }

    let afterStart = content.substring(sidebarStart, sidebarStart + 100);
    let isAside = afterStart.includes('<aside');
    let isDiv = afterStart.includes('<div class="sidebar"') || afterStart.includes('<div class="sidebar ');

    if (isAside) {
        sidebarEnd = content.indexOf('</aside>', sidebarStart) + '</aside>'.length;
    } else if (isDiv) {
        sidebarEnd = content.indexOf('</div>', sidebarStart) + '</div>'.length;
        const mainContentIdx = content.indexOf('<div class="main-content">', sidebarStart);
        if (mainContentIdx !== -1) {
            let idx = mainContentIdx - 1;
            while (idx > sidebarStart && (content[idx] === '\n' || content[idx] === ' ')) idx--;
            if (content.substring(idx - 5, idx + 1) === '</div>') sidebarEnd = idx + 1;
        }
    } else {
        sidebarEnd = content.indexOf('</aside>', sidebarStart) + '</aside>'.length;
    }

    if (sidebarEnd === -1 || sidebarEnd <= sidebarStart) { console.log('SKIP ' + page + ': no sidebar end'); continue; }

    let newSidebarHtml = sidebarHtmlTemplate;
    newSidebarHtml = newSidebarHtml.replace(/class="nav-item active"/g, 'class="nav-item"');
    const activeId = activeMap[page];
    if (activeId) {
        newSidebarHtml = newSidebarHtml.replace('data-dom-id="' + activeId + '"', 'data-dom-id="' + activeId + '" class="nav-item active"');
        newSidebarHtml = newSidebarHtml.replace('class="nav-item" data-dom-id="' + activeId + '" class="nav-item active"', 'class="nav-item active" data-dom-id="' + activeId + '"');
    }
    content = content.substring(0, sidebarStart) + newSidebarHtml + content.substring(sidebarEnd);

    const pageStyleBlocks = [];
    let pSearchIdx = 0;
    while (true) {
        const pStart = content.indexOf('<style>', pSearchIdx);
        if (pStart === -1) break;
        const pEnd = content.indexOf('</style>', pStart);
        pageStyleBlocks.push({ start: pStart, end: pEnd + 9 });
        pSearchIdx = pEnd + 9;
    }

    let sidebarCssStart = -1;
    let sidebarCssEnd = -1;
    for (let i = 0; i < pageStyleBlocks.length; i++) {
        const blockStart = pageStyleBlocks[i].start;
        const blockEnd = pageStyleBlocks[i].end;
        const blockContent = content.substring(blockStart, blockEnd);
        const sidebarIdx = blockContent.indexOf('.sidebar');
        if (sidebarIdx !== -1) {
            sidebarCssStart = blockStart + sidebarIdx;
            const possibleEnds = [
                '        /* Header */',
                '        /* Decorative elements */',
                '        /* Playful cards',
                '        /* Pill buttons',
                '        /* Page header */',
                '        /* Decorative dots */',
                '        /* Chat specific styles */',
                '        /* Whimsical Pastel Overrides */',
                '        /* Scrollbar */',
                '        /* Tags */',
                '        /* Activity item */',
                '        /* Stat cards',
                '        /* Icon bubble */',
                '        /* Form inputs',
                '    </style>'
            ];
            let earliestEnd = blockEnd;
            for (const endPattern of possibleEnds) {
                const endIdx = content.indexOf(endPattern, sidebarCssStart);
                if (endIdx !== -1 && endIdx < earliestEnd) earliestEnd = endIdx;
            }
            sidebarCssEnd = earliestEnd;
            break;
        }
    }

    if (sidebarCssStart !== -1 && sidebarCssEnd !== -1) {
        content = content.substring(0, sidebarCssStart) + sidebarCssTemplate + content.substring(sidebarCssEnd);
    } else {
        console.log('  WARNING: no sidebar CSS in ' + page);
    }

    if (content !== originalContent) {
        fs.writeFileSync(filePath, content, 'utf-8');
        console.log('UPDATED: ' + page);
    } else {
        console.log('NO CHANGE: ' + page);
    }
}
console.log('Done');
