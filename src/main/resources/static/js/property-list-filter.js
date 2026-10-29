(function initCategoryFilterEngine() {
  const searchInput = document.getElementById('keywordSearchInput');
  const clearSearchBtn = document.getElementById('clearKeywordBtn');
  const sortSelect = document.getElementById('sortPropertySelect');
  const filteredCountEl = document.getElementById('filteredCount');
  const gridContainer = document.getElementById('propertyGrid');
  const emptyStateEl = document.getElementById('emptyResultsState');
  const filterIndicator = document.getElementById('activeFilterIndicator');
  const quickPricePills = document.querySelectorAll('.quick-price-pill');
  const priceRadios = document.querySelectorAll('input[name="priceRangeRadio"]');
  const acreageRadios = document.querySelectorAll('input[name="acreageRadio"]');
  const amenityCheckboxes = document.querySelectorAll('input[name="amenityCheckbox"]');
  const savedOnlyFilterBtn = document.getElementById('savedOnlyFilterBtn');
  const paginationBar = document.getElementById('paginationBar');
  const paginationButtons = document.getElementById('paginationButtons');
  const currentPageLabel = document.getElementById('currentPageLabel');
  const totalPagesLabel = document.getElementById('totalPagesLabel');

  if (!gridContainer) return;

  let savedOnly = false;
  let customMaxPrice = null;
  let currentPage = 1;
  const pageSize = 9;

  const cardElements = Array.from(gridContainer.querySelectorAll('.property-grid-item'));

  const cardData = cardElements.map(item => {
    const cardLink = item.querySelector('.property-item-card') || item.querySelector('a');
    const price = parseInt(cardLink?.dataset?.price || '0', 10);
    const acreage = parseFloat(cardLink?.dataset?.acreage || '0');
    const title = (cardLink?.dataset?.title || '').toLowerCase();
    const rawTitle = cardLink?.dataset?.title || '';
    const address = (cardLink?.dataset?.address || '').toLowerCase();
    const rawAddress = cardLink?.dataset?.address || '';
    const category = (cardLink?.dataset?.category || '').toLowerCase();
    const rawCategory = cardLink?.dataset?.category || 'Nhà trọ';
    const slug = cardLink?.dataset?.slug || '';
    const amenities = Array.from(item.querySelectorAll('.amenity-tag')).map(el => el.textContent.trim().toLowerCase());
    const rawAmenities = Array.from(item.querySelectorAll('.amenity-tag')).map(el => el.textContent.trim());
    const id = parseInt(cardLink?.dataset?.id || '0', 10);

    return {
      element: item,
      id: id,
      slug: slug,
      price: price,
      acreage: acreage,
      title: title,
      rawTitle: rawTitle,
      address: address,
      rawAddress: rawAddress,
      category: category,
      rawCategory: rawCategory,
      amenities: amenities,
      rawAmenities: rawAmenities
    };
  });

  function getSavedSlugs() {
    try {
      const list = JSON.parse(localStorage.getItem('tm_wishlist') || '[]');
      return list.map(it => typeof it === 'string' ? it : it.slug);
    } catch (e) {
      return [];
    }
  }

  function getSelectedPriceRange() {
    const checked = document.querySelector('input[name="priceRangeRadio"]:checked');
    return checked ? checked.value : 'all';
  }

  function getSelectedAcreage() {
    const checked = document.querySelector('input[name="acreageRadio"]:checked');
    return checked ? checked.value : 'all';
  }

  function getSelectedAmenities() {
    const checked = Array.from(document.querySelectorAll('input[name="amenityCheckbox"]:checked'));
    return checked.map(cb => cb.value.trim().toLowerCase());
  }

  function matchesPrice(cardPrice, rangeKey) {
    if (customMaxPrice !== null && customMaxPrice > 0) {
      if (customMaxPrice >= 15000000) return cardPrice > 5000000;
      return cardPrice <= customMaxPrice;
    }
    if (rangeKey === 'all') return true;
    if (rangeKey === 'under-2') return cardPrice < 2000000;
    if (rangeKey === '2-3') return cardPrice >= 2000000 && cardPrice <= 3000000;
    if (rangeKey === '3-5') return cardPrice >= 3000000 && cardPrice <= 5000000;
    if (rangeKey === 'over-5') return cardPrice > 5000000;
    return true;
  }

  function matchesAcreage(cardAcreage, acreageKey) {
    if (acreageKey === 'all') return true;
    if (acreageKey === 'under-20') return cardAcreage < 20;
    if (acreageKey === '20-30') return cardAcreage >= 20 && cardAcreage <= 30;
    if (acreageKey === '30-45') return cardAcreage >= 30 && cardAcreage <= 45;
    if (acreageKey === 'over-45') return cardAcreage > 45;
    return true;
  }

  function syncUrlQueryState() {
    if (!window.history || !window.history.replaceState) return;
    const params = new URLSearchParams(window.location.search);
    const kw = (searchInput?.value || '').trim();
    const priceKey = getSelectedPriceRange();
    const sortMode = sortSelect?.value || 'default';

    if (kw) params.set('keyword', kw);
    else params.delete('keyword');

    if (priceKey && priceKey !== 'all') {
      params.set('price', priceKey);
      params.delete('maxPrice');
    } else {
      params.delete('price');
      if (customMaxPrice === null) params.delete('maxPrice');
    }

    if (sortMode && sortMode !== 'default') params.set('sort', sortMode);
    else params.delete('sort');

    if (savedOnly) params.set('saved', '1');
    else params.delete('saved');

    const qs = params.toString();
    const newUrl = window.location.pathname + (qs ? '?' + qs : '');
    window.history.replaceState(null, '', newUrl);
  }

  function renderPagination(totalItems) {
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    if (currentPage > totalPages) currentPage = totalPages;
    if (currentPageLabel) currentPageLabel.textContent = currentPage;
    if (totalPagesLabel) totalPagesLabel.textContent = totalPages;
    if (!paginationBar || !paginationButtons) return;

    if (totalItems === 0) {
      paginationBar.classList.add('hidden');
      return;
    }
    paginationBar.classList.remove('hidden');
    paginationButtons.innerHTML = '';

    for (let p = 1; p <= totalPages; p++) {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.textContent = p;
      btn.className = p === currentPage
        ? 'px-3 py-1.5 rounded-lg bg-[#fe9901] text-white text-sm font-bold tabular-nums border border-[#fe9901] cursor-pointer whitespace-nowrap'
        : 'px-3 py-1.5 rounded-lg bg-white hover:bg-[#fe9901] text-[#1e293b] hover:text-white text-sm font-semibold tabular-nums border border-slate-200 cursor-pointer whitespace-nowrap';
      btn.addEventListener('click', () => {
        currentPage = p;
        applyFilterAndSort(false);
      });
      paginationButtons.appendChild(btn);
    }
  }

  function applyFilterAndSort(resetPage = true) {
    if (resetPage) currentPage = 1;
    const keyword = (searchInput?.value || '').trim().toLowerCase();
    const priceKey = getSelectedPriceRange();
    const acreageKey = getSelectedAcreage();
    const selectedAmenities = getSelectedAmenities();
    const sortMode = sortSelect?.value || 'default';
    const savedSlugs = savedOnly ? getSavedSlugs() : [];

    const matchedItems = [];

    cardData.forEach(card => {
      const matchesKeyword = !keyword || card.title.includes(keyword) || card.address.includes(keyword) || card.category.includes(keyword);
      const matchPrice = matchesPrice(card.price, priceKey);
      const matchAcreage = matchesAcreage(card.acreage, acreageKey);
      const matchSaved = !savedOnly || savedSlugs.includes(card.slug);

      let matchAmenity = true;
      if (selectedAmenities.length > 0) {
        matchAmenity = selectedAmenities.every(needed => card.amenities.includes(needed));
      }

      if (matchesKeyword && matchPrice && matchAcreage && matchAmenity && matchSaved) {
        matchedItems.push(card);
      } else {
        card.element.style.display = 'none';
      }
    });

    if (matchedItems.length > 0) {
      matchedItems.sort((a, b) => {
        if (sortMode === 'price-asc') return a.price - b.price;
        if (sortMode === 'price-desc') return b.price - a.price;
        if (sortMode === 'acreage-desc') return b.acreage - a.acreage;
        return b.id - a.id;
      });

      const totalPages = Math.max(1, Math.ceil(matchedItems.length / pageSize));
      if (currentPage > totalPages) currentPage = totalPages;
      const startIdx = (currentPage - 1) * pageSize;
      const endIdx = startIdx + pageSize;

      matchedItems.forEach((item, index) => {
        gridContainer.appendChild(item.element);
        item.element.style.display = (index >= startIdx && index < endIdx) ? '' : 'none';
      });
    }

    const visibleCount = matchedItems.length;
    if (filteredCountEl) filteredCountEl.textContent = visibleCount;

    if (visibleCount === 0) {
      emptyStateEl?.classList.remove('hidden');
      gridContainer?.classList.add('hidden');
    } else {
      emptyStateEl?.classList.add('hidden');
      gridContainer?.classList.remove('hidden');
    }

    if (clearSearchBtn) {
      if (keyword.length > 0) clearSearchBtn.classList.remove('hidden');
      else clearSearchBtn.classList.add('hidden');
    }

    const isFiltered = Boolean(
      keyword.length > 0 ||
      priceKey !== 'all' ||
      customMaxPrice !== null ||
      acreageKey !== 'all' ||
      selectedAmenities.length > 0 ||
      savedOnly
    );

    if (filterIndicator) {
      if (isFiltered) filterIndicator.classList.remove('hidden');
      else filterIndicator.classList.add('hidden');
    }

    if (savedOnlyFilterBtn) {
      savedOnlyFilterBtn.className = savedOnly
        ? 'px-2.5 py-1.5 rounded-lg text-xs font-semibold bg-[#ea580c] text-white border-0 transition-colors inline-flex items-center gap-1 cursor-pointer whitespace-nowrap'
        : 'px-2.5 py-1.5 rounded-lg text-xs font-medium bg-[#f8fafc] hover:bg-[#ea580c] text-[#1e293b] hover:text-white border border-slate-200 transition-colors inline-flex items-center gap-1 cursor-pointer whitespace-nowrap';
    }

    renderPagination(visibleCount);
    syncUrlQueryState();
    updateListMapEmbed();
  }

  function updateListMapEmbed() {
    const mapPanel = document.getElementById('listMapViewPanel');
    const mapIframe = document.getElementById('listMapIframe');
    const mapQueryText = document.getElementById('listMapQueryText');
    if (!mapPanel || !mapIframe) return;
    const catName = mapPanel.getAttribute('data-category-name') || 'Phòng trọ';
    const kw = (searchInput?.value || '').trim();
    const fullQuery = kw ? (catName + ' ' + kw + ' Việt Nam') : (catName + ' Vinh Nghệ An');
    if (mapQueryText) {
      mapQueryText.textContent = kw ? (catName + ' · "' + kw + '"') : catName;
    }
    if (!mapPanel.classList.contains('hidden')) {
      const encoded = encodeURIComponent(fullQuery);
      const nextSrc = 'https://maps.google.com/maps?q=' + encoded + '&t=&z=13&ie=UTF8&iwloc=&output=embed';
      if (mapIframe.getAttribute('data-last-query') !== fullQuery) {
        mapIframe.src = nextSrc;
        mapIframe.setAttribute('data-last-query', fullQuery);
      }
    }
  }

  window.toggleListMapView = function () {
    const mapPanel = document.getElementById('listMapViewPanel');
    const mapBtn = document.getElementById('toggleListMapBtn');
    if (!mapPanel) return;
    const isHidden = mapPanel.classList.toggle('hidden');
    if (mapBtn) {
      mapBtn.className = !isHidden
        ? 'px-2.5 py-1.5 rounded-lg text-xs font-semibold bg-[#ea580c] text-white border-0 transition-colors inline-flex items-center gap-1 cursor-pointer whitespace-nowrap'
        : 'px-2.5 py-1.5 rounded-lg text-xs font-semibold bg-[#fff7ed] text-[#ea580c] hover:bg-[#ea580c] hover:text-white border border-[#fed7aa] transition-colors inline-flex items-center gap-1 cursor-pointer whitespace-nowrap';
    }
    if (!isHidden) {
      updateListMapEmbed();
    }
  };

  window.fuzzyRelaxFilters = function () {
    savedOnly = false;
    const defaultAcreage = document.querySelector('input[name="acreageRadio"][value="all"]');
    if (defaultAcreage) defaultAcreage.checked = true;
    amenityCheckboxes.forEach(cb => cb.checked = false);
    applyFilterAndSort();
  };

  function syncQuickPrice(val, clearCustomMax = true) {
    if (clearCustomMax) customMaxPrice = null;
    quickPricePills.forEach(pill => {
      if (pill.dataset.quickPrice === val) {
        pill.className = 'quick-price-pill active px-2.5 py-1.5 rounded-lg text-xs font-semibold bg-[#ea580c] text-white border-0 transition-colors cursor-pointer whitespace-nowrap';
      } else {
        pill.className = 'quick-price-pill px-2.5 py-1.5 rounded-lg text-xs font-medium bg-[#f8fafc] hover:bg-[#ea580c] text-[#1e293b] hover:text-white border border-slate-200 transition-colors cursor-pointer whitespace-nowrap';
      }
    });

    const targetRadio = document.querySelector(`input[name="priceRangeRadio"][value="${val}"]`);
    if (targetRadio) targetRadio.checked = true;

    applyFilterAndSort();
  }

  quickPricePills.forEach(pill => {
    pill.addEventListener('click', () => {
      syncQuickPrice(pill.dataset.quickPrice);
    });
  });

  priceRadios.forEach(radio => {
    radio.addEventListener('change', () => {
      syncQuickPrice(radio.value);
    });
  });

  acreageRadios.forEach(radio => {
    radio.addEventListener('change', () => applyFilterAndSort());
  });

  amenityCheckboxes.forEach(cb => {
    cb.addEventListener('change', () => applyFilterAndSort());
  });

  searchInput?.addEventListener('input', () => applyFilterAndSort());

  clearSearchBtn?.addEventListener('click', () => {
    if (searchInput) searchInput.value = '';
    applyFilterAndSort();
  });

  sortSelect?.addEventListener('change', () => applyFilterAndSort());

  savedOnlyFilterBtn?.addEventListener('click', () => {
    savedOnly = !savedOnly;
    applyFilterAndSort();
  });

  window.addEventListener('wishlist-updated', () => {
    if (savedOnly) applyFilterAndSort(false);
  });

  window.resetAllFilters = function() {
    if (searchInput) searchInput.value = '';
    savedOnly = false;
    customMaxPrice = null;
    syncQuickPrice('all');
    const defaultAcreage = document.querySelector('input[name="acreageRadio"][value="all"]');
    if (defaultAcreage) defaultAcreage.checked = true;
    amenityCheckboxes.forEach(cb => cb.checked = false);
    if (sortSelect) sortSelect.value = 'default';
    applyFilterAndSort();
  };

  /* Initialize from URL query parameters on load */
  function initFiltersFromUrl() {
    const params = new URLSearchParams(window.location.search);
    const kw = params.get('keyword');
    const priceParam = params.get('price');
    const maxPriceParam = params.get('maxPrice');
    const sortParam = params.get('sort');
    const savedParam = params.get('saved');

    if (kw && searchInput) {
      searchInput.value = kw;
    }
    if (sortParam && sortSelect) {
      sortSelect.value = sortParam;
    }
    if (savedParam === '1') {
      savedOnly = true;
    }
    if (priceParam) {
      syncQuickPrice(priceParam);
      return;
    }
    if (maxPriceParam) {
      const mp = parseInt(maxPriceParam, 10);
      if (!isNaN(mp) && mp > 0) {
        customMaxPrice = mp;
        if (mp <= 2000000) syncQuickPrice('under-2', false);
        else if (mp <= 3000000) syncQuickPrice('2-3', false);
        else if (mp <= 5000000) syncQuickPrice('3-5', false);
        else syncQuickPrice('over-5', false);
        return;
      }
    }
    applyFilterAndSort();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initFiltersFromUrl);
  } else {
    initFiltersFromUrl();
  }

  /* ── Compare Rooms Drawer & Modal Logic (Up to 3 Rooms) ── */
  const compareRooms = [];

  function updateCompareUI() {
    const bar = document.getElementById('compareDrawerBar');
    const countEl = document.getElementById('compareCountLabel');
    const listEl = document.getElementById('compareSelectedList');
    if (countEl) countEl.textContent = compareRooms.length;

    if (bar) {
      if (compareRooms.length > 0) bar.classList.remove('hidden');
      else bar.classList.add('hidden');
    }

    if (listEl) {
      listEl.innerHTML = '';
      compareRooms.forEach(rm => {
        const chip = document.createElement('div');
        chip.className = 'px-3 py-1 bg-[#1f2b4d] text-[#ffffff] flex items-center gap-2';
        chip.innerHTML = `<span class="max-w-[160px] truncate">${rm.title}</span>
          <button type="button" class="text-[#e3e1dc] hover:text-[#f7941d] cursor-pointer" onclick="removeCompareRoomBySlug('${rm.slug}')">
            <i class="fa-solid fa-xmark"></i>
          </button>`;
        listEl.appendChild(chip);
      });
    }

    document.querySelectorAll('.compare-room-btn').forEach(btn => {
      const slug = btn.getAttribute('data-slug');
      const active = compareRooms.some(r => r.slug === slug);
      btn.className = active
        ? 'compare-room-btn estate-action-pill bg-[#fe9901] text-white'
        : 'compare-room-btn estate-action-pill';
    });
  }

  window.toggleCompareRoom = function(btn) {
    if (!btn) return;
    const slug = btn.getAttribute('data-slug') || '';
    const existingIdx = compareRooms.findIndex(r => r.slug === slug);
    if (existingIdx >= 0) {
      compareRooms.splice(existingIdx, 1);
      updateCompareUI();
      return;
    }
    if (compareRooms.length >= 3) {
      compareRooms.shift();
    }
    const cardItem = btn.closest('.property-grid-item');
    const amenities = cardItem
      ? Array.from(cardItem.querySelectorAll('.amenity-tag')).map(el => el.textContent.trim())
      : [];
    compareRooms.push({
      slug: slug,
      title: btn.getAttribute('data-title') || 'Phòng trọ',
      price: parseInt(btn.getAttribute('data-price') || '0', 10),
      acreage: btn.getAttribute('data-acreage') || '0',
      category: btn.getAttribute('data-category') || 'Nhà trọ',
      address: btn.getAttribute('data-address') || '',
      amenities: amenities
    });
    updateCompareUI();
  };

  window.removeCompareRoomBySlug = function(slug) {
    const idx = compareRooms.findIndex(r => r.slug === slug);
    if (idx >= 0) {
      compareRooms.splice(idx, 1);
      updateCompareUI();
    }
  };

  window.clearCompareRooms = function() {
    compareRooms.length = 0;
    updateCompareUI();
    closeCompareModal();
  };

  window.openCompareModal = function() {
    const modal = document.getElementById('compareModal');
    const thead = document.getElementById('compareTableHead');
    const tbody = document.getElementById('compareTableBody');
    if (!modal || !thead || !tbody || compareRooms.length === 0) return;

    let headHtml = '<tr><th class="p-4 font-semibold w-44">Tiêu chí đối chiếu</th>';
    compareRooms.forEach(rm => {
      headHtml += `<th class="p-4 font-semibold">
        <a href="/public/property/${rm.slug}" class="text-sm text-[#1f2b4d] hover:text-[#f7941d] line-clamp-1">${rm.title}</a>
      </th>`;
    });
    headHtml += '</tr>';
    thead.innerHTML = headHtml;

    const rows = [
      { label: 'Giá thuê', render: r => `<span class="font-semibold text-[#1f2b4d] tabular-nums">${Number(r.price).toLocaleString('vi-VN')} đ / tháng</span>` },
      { label: 'Diện tích', render: r => `<span class="tabular-nums">${r.acreage} m²</span>` },
      { label: 'Danh mục', render: r => `<span>${r.category}</span>` },
      { label: 'Địa chỉ', render: r => `<span class="text-[#6a7186]">${r.address}</span>` },
      { label: 'Tiện nghi', render: r => r.amenities.length > 0 ? r.amenities.map(a => `<span class="inline-block px-2 py-0.5 bg-[#f4f4f2] text-[#1f2b4d] mr-1 mb-1">${a}</span>`).join('') : '<span class="text-[#6a7186]">Cơ bản</span>' }
    ];

    tbody.innerHTML = rows.map(row => {
      let tr = `<tr><td class="p-4 font-semibold text-[#1f2b4d] bg-[#f4f4f2]/60">${row.label}</td>`;
      compareRooms.forEach(rm => {
        tr += `<td class="p-4 align-top">${row.render(rm)}</td>`;
      });
      tr += '</tr>';
      return tr;
    }).join('');

    modal.classList.remove('hidden');
    document.body.style.overflow = 'hidden';
  };

  window.closeCompareModal = function() {
    const modal = document.getElementById('compareModal');
    if (modal) modal.classList.add('hidden');
    document.body.style.overflow = '';
  };
})();
