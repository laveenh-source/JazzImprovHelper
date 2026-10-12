// Jazz Chart Analyzer: a small page that talks to the REST API.
// All text that comes from a file or the server is inserted as plain text, never parsed as markup.
(function () {
  'use strict';

  const API = '/api/v1';
  const $ = (id) => document.getElementById(id);

  function el(tag, attrs, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs || {})) {
      if (key === 'class') node.className = value;
      else if (key === 'text') node.textContent = value;
      else node.setAttribute(key, value);
    }
    for (const child of children) node.append(child);
    return node;
  }

  function setStatus(node, text, kind) {
    node.textContent = text;
    node.className = 'status' + (kind ? ' ' + kind : '');
  }

  // ---------------------------------------------------------------- API calls

  async function api(path, options) {
    const response = await fetch(API + path, options);
    if (!response.ok) {
      let message = response.status + ' ' + response.statusText;
      try { message = (await response.json()).message || message; } catch (ignored) { /* not JSON */ }
      const error = new Error(message);
      error.status = response.status;
      throw error;
    }
    return response;
  }

  const getJson = async (path) => (await api(path)).json();

  // ---------------------------------------------------------------- upload and polling

  async function analyse(file) {
    const form = new FormData();
    form.append('file', file);
    const upload = await (await api('/songs', { method: 'POST', body: form })).json();

    let analysis = await getJson('/analyses/' + upload.analysisId);
    for (let waited = 0; (analysis.status === 'PENDING' || analysis.status === 'RUNNING') && waited < 60000; waited += 700) {
      setStatus($('status'), 'Analysing… (' + analysis.status.toLowerCase() + ')');
      await new Promise((resolve) => setTimeout(resolve, 700));
      analysis = await getJson('/analyses/' + upload.analysisId);
    }
    return { upload, analysis };
  }

  $('upload-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const file = $('file-input').files[0];
    if (!file) return;
    $('upload-button').disabled = true;
    setStatus($('status'), 'Uploading…');
    try {
      const { upload, analysis } = await analyse(file);
      if (analysis.status === 'COMPLETE') {
        setStatus($('status'), upload.duplicate ? 'This file was analysed before; showing the saved result.' : 'Done.', 'ok');
        showResult(analysis, await file.text());
        loadLibrary();
      } else if (analysis.status === 'FAILED') {
        setStatus($('status'), analysis.errorMessage, 'error');
      } else {
        setStatus($('status'), 'The analysis is taking longer than expected. Check the library in a moment.', 'error');
      }
    } catch (error) {
      setStatus($('status'), error.message, 'error');
    } finally {
      $('upload-button').disabled = false;
    }
  });

  // ---------------------------------------------------------------- showing a result

  function showResult(analysis, xmlText) {
    $('result').hidden = false;
    $('result-title').textContent = analysis.title;
    $('result-key').textContent = 'Main key: ' + analysis.detectedKey;

    const regions = $('regions');
    regions.replaceChildren(...analysis.keyRegions.map((r) => el('li', {},
      el('strong', { text: r.key + ', bars ' + r.startMeasure + '–' + r.endMeasure }),
      el('span', { text: r.reason + ' (confidence ' + r.confidence.toFixed(2) + ')' }))));

    fillChordTable(analysis.chords);
    drawScore(xmlText, analysis.chords);
    $('result').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function fillChordTable(chords) {
    $('chords').tBodies[0].replaceChildren(...chords.map((c) => {
      const scales = c.scales.length === 0
        ? el('span', { class: 'notes', text: 'no suggestion for this chord' })
        : el('details', {},
            el('summary', { text: c.scales[0].name }),
            el('ol', {}, ...c.scales.map((s) => el('li', {},
              el('strong', { text: s.name }), el('span', { class: 'notes', text: '  ' + s.notes.join(' ') }),
              el('div', { text: s.reason })))));
      return el('tr', {},
        el('td', { text: String(c.measure) }), el('td', { text: c.symbol }), el('td', { text: c.roman }),
        el('td', { title: c.functionReason, text: c.function }), el('td', { text: c.localKey }), el('td', {}, scales));
    }));
  }

  // ---------------------------------------------------------------- the score (OpenSheetMusicDisplay)

  let renderToken = 0;

  async function drawScore(xmlText, chords) {
    const token = ++renderToken;
    const error = $('score-error');
    error.hidden = true;
    $('score').replaceChildren();
    $('overlay').replaceChildren();
    try {
      if (typeof opensheetmusicdisplay === 'undefined') throw new Error('The score library did not load.');
      const osmd = new opensheetmusicdisplay.OpenSheetMusicDisplay($('score'), {
        autoResize: false, drawTitle: false, drawPartNames: false, drawingParameters: 'compact',
      });
      await osmd.load(xmlText);
      osmd.EngravingRules.MinimumDistanceBetweenSystems = 16;   // leave room above each system for our labels
      osmd.render();
      if (token === renderToken) placeLabels(osmd, xmlText, chords);
    } catch (e) {
      error.textContent = 'The score could not be drawn (' + e.message + '). The chord table below has the full analysis.';
      error.hidden = false;
    }
  }

  // Bar numbers in the file can be any string, so map each analysed bar to its position in the score.
  function barIndexByNumber(xmlText) {
    const doc = new DOMParser().parseFromString(xmlText, 'application/xml');
    const part = Array.from(doc.getElementsByTagName('part')).find((p) => p.getElementsByTagName('harmony').length > 0)
      || doc.getElementsByTagName('part')[0];
    const map = new Map();
    Array.from(part ? part.getElementsByTagName('measure') : []).forEach((m, index) => {
      const n = parseInt(m.getAttribute('number'), 10);
      map.set(Number.isNaN(n) ? index + 1 : n, index);
    });
    const beats = doc.getElementsByTagName('beats')[0];
    const beatType = doc.getElementsByTagName('beat-type')[0];
    return { map, beatsPerBar: beats ? parseInt(beats.textContent, 10) || 4 : 4, beatType: beatType ? parseInt(beatType.textContent, 10) || 4 : 4 };
  }

  // Labels sit above the staff (and above the chord symbols the score library draws). A label starts at its chord;
  // if it would overlap an earlier label on the same system it moves up a row instead.
  const LABEL_BASE = 78;     // px above the top of the staff for the first row
  const LABEL_ROW = 36;      // px per extra row
  const LABEL_GAP = 4;

  function placeLabels(osmd, xmlText, chords) {
    const { map, beatsPerBar } = barIndexByNumber(xmlText);
    const scale = 10 * osmd.zoom;                       // OSMD measures in units of 10 px at zoom 1
    const measures = osmd.GraphicSheet.MeasureList;
    const overlay = $('overlay');
    const svg = $('score').querySelector('svg');
    if (!svg) return;
    const wrapBox = $('score-wrap').getBoundingClientRect();
    const svgBox = svg.getBoundingClientRect();
    const offsetX = svgBox.left - wrapBox.left;
    const offsetY = svgBox.top - wrapBox.top;
    overlay.style.height = (offsetY + svgBox.height) + 'px';

    const rowsPerSystem = new Map();                    // staff top (px) -> rows of already placed label extents
    for (const chord of chords) {
      const index = map.get(chord.measure);
      const bar = index === undefined || !measures[index] ? null : measures[index][0];
      if (!bar) continue;
      const box = bar.PositionAndShape;
      const barWidth = box.Size.width * scale;
      const x = offsetX + box.AbsolutePosition.x * scale + 6 + ((chord.beat - 1) / beatsPerBar) * Math.max(barWidth - 12, 0);
      const staffTop = box.AbsolutePosition.y * scale;

      const first = chord.scales[0];
      const label = el('div', { class: 'chord-label', title: first ? first.name + ': ' + first.reason : '' },
        el('b', { text: chord.roman }), el('span', { text: first ? first.name : '' }));
      label.style.left = x + 'px';
      overlay.append(label);                            // in the page now, so its width can be measured
      const width = label.offsetWidth;

      const key = Math.round(staffTop);
      const rows = rowsPerSystem.get(key) || [];
      let row = 0;
      while (row < rows.length && rows[row].some((r) => x < r.right + LABEL_GAP && x + width > r.left - LABEL_GAP)) row++;
      (rows[row] = rows[row] || []).push({ left: x, right: x + width });
      rowsPerSystem.set(key, rows);

      label.style.top = Math.max(offsetY + staffTop - LABEL_BASE - row * LABEL_ROW, 0) + 'px';
    }
  }

  // ---------------------------------------------------------------- library

  let page = 0;
  const PAGE_SIZE = 10;

  async function loadLibrary() {
    const key = $('library-key').value.trim();
    const query = '?size=' + PAGE_SIZE + '&page=' + page + (key ? '&key=' + encodeURIComponent(key) : '');
    try {
      const data = await getJson('/songs' + query);
      $('library').tBodies[0].replaceChildren(...data.content.map((song) => {
        const open = el('button', { type: 'button', class: 'link', text: 'View' });
        open.disabled = song.latestStatus !== 'COMPLETE';
        open.addEventListener('click', () => openSong(song.id));
        return el('tr', {},
          el('td', { text: song.title }), el('td', { text: song.detectedKey || '' }),
          el('td', { text: (song.latestStatus || '').toLowerCase() }),
          el('td', { text: new Date(song.uploadedAt).toLocaleString() }), el('td', {}, open));
      }));
      $('page-info').textContent = data.totalElements === 0 ? 'No songs yet' : 'Page ' + (data.page + 1) + ' of ' + data.totalPages;
      $('prev-page').disabled = data.page === 0;
      $('next-page').disabled = data.page + 1 >= data.totalPages;
    } catch (e) {
      $('page-info').textContent = e.message;
    }
  }

  async function openSong(songId) {
    try {
      const [song, chords, file] = await Promise.all([
        getJson('/songs/' + songId), getJson('/songs/' + songId + '/chords'), api('/songs/' + songId + '/file').then((r) => r.text())]);
      const summary = song.latestAnalysis;
      showResult({ title: song.title, detectedKey: summary.detectedKey, keyRegions: summary.keyRegions, chords: chords.chords }, file);
    } catch (e) {
      setStatus($('status'), e.message, 'error');
    }
  }

  $('library-form').addEventListener('submit', (event) => { event.preventDefault(); page = 0; loadLibrary(); });
  $('prev-page').addEventListener('click', () => { page = Math.max(0, page - 1); loadLibrary(); });
  $('next-page').addEventListener('click', () => { page += 1; loadLibrary(); });

  // ---------------------------------------------------------------- progression search

  $('search-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const key = $('search-key').value.trim();
    const query = '?pattern=' + encodeURIComponent($('pattern').value) + (key ? '&key=' + encodeURIComponent(key) : '') + '&size=50';
    setStatus($('search-status'), 'Searching…');
    try {
      const data = await getJson('/search/progressions' + query);
      setStatus($('search-status'), data.totalElements + ' match' + (data.totalElements === 1 ? '' : 'es'), 'ok');
      $('search-results').replaceChildren(...data.content.map((m) => {
        const open = el('button', { type: 'button', class: 'link', text: m.title });
        open.addEventListener('click', () => openSong(m.songId));
        return el('li', {}, open, el('span', { class: 'notes', text: '  in ' + m.key + ', bars ' + m.startMeasure + '–' + m.endMeasure + ': ' + m.chords }));
      }));
    } catch (e) {
      setStatus($('search-status'), e.message, 'error');
      $('search-results').replaceChildren();
    }
  });

  loadLibrary();
})();
