// Shared actions for the packaged WebView and browser regression harness.
function toolNumber(id) {
    const input = document.getElementById(id);
    const n = Number(input.value);
    if (!input.value.trim() || !Number.isFinite(n) ||
        (input.min !== '' && n < Number(input.min)) ||
        (input.max !== '' && n > Number(input.max))) {
        throw new Error('Enter a valid value for ' + (document.querySelector('label[for="'+id+'"]')?.textContent || id));
    }
    return n;
}

async function copyToolText(text, button) {
    const original = button.textContent;
    try {
        if (!text || text.startsWith('# Select')) throw new Error('Nothing to copy');
        if (window.Android?.copyText) {
            if (!Android.copyText(text)) throw new Error('Clipboard unavailable');
        } else {
            await navigator.clipboard.writeText(text);
        }
        button.textContent = 'Copied!';
    } catch (error) {
        button.textContent = 'Copy failed — select the text';
    }
    setTimeout(() => { button.textContent = original; }, 2500);
}

function saveToolReport(filename, html) {
    // Reports are standalone documents: remove interactive handlers from copied results.
    const doc = new DOMParser().parseFromString(html, 'text/html');
    doc.querySelectorAll('script,iframe,object,button').forEach(el => el.remove());
    doc.querySelectorAll('*').forEach(el => [...el.attributes].forEach(a => {
        if (a.name.startsWith('on')) el.removeAttribute(a.name);
    }));
    const content = '<!DOCTYPE html>\n' + doc.documentElement.outerHTML;
    if (window.Android?.saveReport) {
        Android.saveReport(filename, content);
        return;
    }
    const url = URL.createObjectURL(new Blob([content], {type:'text/html;charset=utf-8'}));
    const a = document.createElement('a');
    a.href = url; a.download = filename; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
}

async function toolFetch(url, options = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 12000);
    try {
        const response = await fetch(url, {...options, signal:controller.signal});
        if (!response.ok) throw new Error('Service returned ' + response.status);
        return await response.json();
    } finally { clearTimeout(timer); }
}
