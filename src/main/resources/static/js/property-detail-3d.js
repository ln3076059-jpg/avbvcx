let currentLightboxIndex = 0;

function getGalleryPhotoItems() {
  return Array.from(document.querySelectorAll('.photo-strip-btn')).map((el, i) => ({
    src: el.getAttribute('data-img') || '',
    caption: el.getAttribute('data-caption') || ('Ảnh thực tế ' + (i + 1))
  }));
}

function openPhotoLightbox(idx) {
  const items = getGalleryPhotoItems();
  if (items.length === 0) return;
  currentLightboxIndex = ((idx % items.length) + items.length) % items.length;
  const item = items[currentLightboxIndex];
  const modal = document.getElementById('photoLightboxModal');
  const img = document.getElementById('lightboxMainImg');
  const counter = document.getElementById('lightboxCounterLabel');
  const caption = document.getElementById('lightboxCaptionLabel');
  if (img) img.src = item.src;
  if (counter) counter.textContent = (currentLightboxIndex + 1) + ' / ' + items.length;
  if (caption) caption.textContent = item.caption;
  if (modal) {
    modal.classList.remove('hidden');
    document.body.style.overflow = 'hidden';
  }
}

function closePhotoLightbox() {
  const modal = document.getElementById('photoLightboxModal');
  if (modal) modal.classList.add('hidden');
  document.body.style.overflow = '';
}

function navigatePhotoLightbox(delta) {
  openPhotoLightbox(currentLightboxIndex + delta);
}

document.addEventListener('keydown', function (e) {
  const modal = document.getElementById('photoLightboxModal');
  if (!modal || modal.classList.contains('hidden')) return;
  if (e.key === 'Escape') closePhotoLightbox();
  if (e.key === 'ArrowRight') navigatePhotoLightbox(1);
  if (e.key === 'ArrowLeft') navigatePhotoLightbox(-1);
});

function selectPropertyPhoto(btn, idx) {
  const src = btn.getAttribute('data-img');
  const main = document.getElementById('mainGalleryImage');
  if (src && main) main.src = src;
  if (window.update3DWallPhoto && src) {
    window.update3DWallPhoto(src);
  }
  document.getElementById('inspectorTitle').textContent = 'Đã cập nhật ảnh thực tế lên tường 3D';
  document.getElementById('inspectorDesc').textContent = 'Hình ảnh khảo sát bạn chọn đã được ánh xạ trực tiếp lên khung tranh trong mô hình 3D.';
  openPhotoLightbox(typeof idx === 'number' ? idx : 0);
}

function appendVisitScheduleOnSubmit() {
  const dateVal = document.getElementById('visitDateInput')?.value || '';
  const timeVal = document.getElementById('visitTimeSelect')?.value || '';
  const noteEl = document.getElementById('contactNote');
  if (!noteEl) return;
  if (dateVal || timeVal) {
    const scheduleTag = `[Lịch hẹn xem phòng: ${dateVal || 'Sớm nhất'}${timeVal ? ' - ' + timeVal : ''}]`;
    if (!noteEl.value.includes('[Lịch hẹn xem phòng:')) {
      noteEl.value = (noteEl.value ? noteEl.value.trim() + ' ' : '') + scheduleTag;
    }
  }
}

/* ── 100% Database-Driven Three.js 3D Room / Apartment Twin ── */
(function initProperty3DScene() {
  const mount = document.getElementById('threeCanvasMount');
  const showcase = document.getElementById('property3dShowcase');
  const hotspotsLayer = document.getElementById('threeHotspotsLayer');
  if (!mount || !showcase || typeof THREE === 'undefined') return;

  // 1. Read exact Database Amenities (`property_amenity`) & Property Specs
  const dbAmenityNodes = Array.from(document.querySelectorAll('#dbFacilityBridge .db-amenity-node'));
  const dbAmenities = dbAmenityNodes.map(n => (n.getAttribute('data-name') || '').trim());
  const dbAmenitiesLower = dbAmenities.map(a => a.toLowerCase());

  function hasAmenityKeyword(keywords) {
    return dbAmenitiesLower.some(a => keywords.some(kw => a.indexOf(kw) !== -1));
  }

  // Check each of the 10 database amenities in `amenities` table:
  const hasWifi = hasAmenityKeyword(['wifi', 'internet', 'mạng']);
  const hasToiletInside = hasAmenityKeyword(['vệ sinh trong', 'wc', 'toilet', 'khép kín']);
  const hasShower = hasAmenityKeyword(['phòng tắm', 'vòi sen', 'tắm']);
  const hasWaterHeater = hasAmenityKeyword(['bình nóng lạnh', 'nóng lạnh', 'máy nước nóng']);
  const hasKitchen = hasAmenityKeyword(['kệ bếp', 'bếp']);
  const hasAC = hasAmenityKeyword(['điều hòa', 'máy lạnh']);
  const hasBed = hasAmenityKeyword(['giường nệm', 'giường', 'nệm']);
  const hasParking = hasAmenityKeyword(['bãi để xe', 'để xe', 'nhà xe']);
  const hasWardrobe = hasAmenityKeyword(['tủ quần áo', 'tủ đồ', 'tủ']);
  const hasCCTV = hasAmenityKeyword(['camera', 'an ninh']);

  const rawAcreage = parseFloat(showcase.getAttribute('data-acreage')) || 28;
  const clampedArea = Math.max(18, Math.min(85, rawAcreage));
  const roomW = Number(Math.sqrt(clampedArea * 1.28).toFixed(1));
  const roomD = Number((clampedArea / roomW).toFixed(1));
  const wallH = 3.1;

  const hudDim = document.getElementById('hudDimensionsText');
  if (hudDim) {
    hudDim.textContent =
      'Diện tích: ' + rawAcreage + ' m² (' + roomW + 'm × ' + roomD + 'm) · ' +
      dbAmenities.length + ' tiện ích có sẵn';
  }

  // 2. Scene, Camera, Renderer
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x0b101e);
  scene.fog = new THREE.FogExp2(0x0b101e, 0.02);

  const initW = mount.clientWidth || 800;
  const initH = mount.clientHeight || 500;
  const camera = new THREE.PerspectiveCamera(40, initW / initH, 0.1, 100);

  const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
  renderer.setSize(initW, initH);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.outputEncoding = THREE.sRGBEncoding;
  mount.innerHTML = '';
  mount.appendChild(renderer.domElement);

  // 3. Procedural Hardwood & Tile Textures
  function createWoodFloorTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 512;
    canvas.height = 512;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#c8a982';
    ctx.fillRect(0, 0, 512, 512);
    const planks = 8;
    const plankH = 512 / planks;
    for (let i = 0; i < planks; i++) {
      ctx.fillStyle = i % 2 === 0 ? '#c3a279' : '#ccaE86';
      ctx.fillRect(0, i * plankH, 512, plankH - 2);
      ctx.fillStyle = '#9e7f58';
      ctx.fillRect(0, (i + 1) * plankH - 2, 512, 2);
      const jointX = ((i * 173) % 400) + 50;
      ctx.fillRect(jointX, i * plankH, 2, plankH);
    }
    const tex = new THREE.CanvasTexture(canvas);
    tex.wrapS = THREE.RepeatWrapping;
    tex.wrapT = THREE.RepeatWrapping;
    tex.repeat.set(roomW / 1.8, roomD / 1.8);
    return tex;
  }

  function createRugTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 256;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#e3e1dc';
    ctx.fillRect(0, 0, 256, 256);
    ctx.strokeStyle = '#1f2b4d';
    ctx.lineWidth = 10;
    ctx.strokeRect(18, 18, 220, 220);
    ctx.strokeStyle = '#f7941d';
    ctx.lineWidth = 4;
    ctx.strokeRect(34, 34, 188, 188);
    return new THREE.CanvasTexture(canvas);
  }

  // 4. Materials Palette
  const matFloor = new THREE.MeshStandardMaterial({
    map: createWoodFloorTexture(),
    roughness: 0.55,
    metalness: 0.05
  });
  const matWall = new THREE.MeshStandardMaterial({
    color: 0xf4f4f2,
    roughness: 0.85
  });
  const matWallCutaway = new THREE.MeshStandardMaterial({
    color: 0xe3e1dc,
    roughness: 0.8,
    transparent: true,
    opacity: 0.24
  });
  const matDarkNavy = new THREE.MeshStandardMaterial({
    color: 0x1f2b4d,
    roughness: 0.5
  });
  const matAccentOrange = new THREE.MeshStandardMaterial({
    color: 0xf7941d,
    roughness: 0.4
  });
  const matWalnut = new THREE.MeshStandardMaterial({
    color: 0x6e4b32,
    roughness: 0.55
  });
  const matOak = new THREE.MeshStandardMaterial({
    color: 0xa67c52,
    roughness: 0.5
  });
  const matWhiteFabric = new THREE.MeshStandardMaterial({
    color: 0xffffff,
    roughness: 0.85
  });
  const matMetal = new THREE.MeshStandardMaterial({
    color: 0xd0d5dd,
    roughness: 0.3,
    metalness: 0.75
  });
  const matGlass = new THREE.MeshPhysicalMaterial({
    color: 0xdbeafe,
    transparent: true,
    opacity: 0.38,
    roughness: 0.1,
    metalness: 0.1
  });

  const roomGroup = new THREE.Group();
  scene.add(roomGroup);

  const interactables = [];
  const hotspotData = [];

  function registerDBFacility(mesh, posVec, label, title, desc, matchKeywords) {
    mesh.userData = { title: title, desc: desc };
    interactables.push(mesh);
    hotspotData.push({
      pos: posVec,
      label: label,
      title: title,
      desc: desc,
      keywords: matchKeywords || [label.toLowerCase()]
    });
  }

  // 5. Base Architectural Shell (Always present: Floor, Walls, Window, Wall Photo Frame)
  const gridHelper = new THREE.GridHelper(24, 24, 0x2c3b63, 0x18223d);
  gridHelper.position.y = -0.16;
  scene.add(gridHelper);

  const slabGeo = new THREE.BoxGeometry(roomW + 0.36, 0.16, roomD + 0.36);
  const slabMesh = new THREE.Mesh(slabGeo, new THREE.MeshStandardMaterial({ color: 0x1e2638, roughness: 0.9 }));
  slabMesh.position.y = -0.08;
  slabMesh.receiveShadow = true;
  roomGroup.add(slabMesh);

  const floorMesh = new THREE.Mesh(new THREE.BoxGeometry(roomW, 0.04, roomD), matFloor);
  floorMesh.position.y = 0.02;
  floorMesh.receiveShadow = true;
  roomGroup.add(floorMesh);

  const wallThick = 0.14;
  const backWall = new THREE.Mesh(new THREE.BoxGeometry(roomW + wallThick, wallH, wallThick), matWall);
  backWall.position.set(0, wallH / 2, -roomD / 2 - wallThick / 2);
  backWall.castShadow = true;
  backWall.receiveShadow = true;
  roomGroup.add(backWall);

  const leftWall = new THREE.Mesh(new THREE.BoxGeometry(wallThick, wallH, roomD + wallThick), matWall);
  leftWall.position.set(-roomW / 2 - wallThick / 2, wallH / 2, 0);
  leftWall.castShadow = true;
  leftWall.receiveShadow = true;
  roomGroup.add(leftWall);

  const frontCurb = new THREE.Mesh(new THREE.BoxGeometry(roomW + wallThick, 0.35, wallThick), matWallCutaway);
  frontCurb.position.set(0, 0.175, roomD / 2 + wallThick / 2);
  roomGroup.add(frontCurb);

  const rightCurb = new THREE.Mesh(new THREE.BoxGeometry(wallThick, 0.35, roomD + wallThick), matWallCutaway);
  rightCurb.position.set(roomW / 2 + wallThick / 2, 0.175, 0);
  roomGroup.add(rightCurb);

  // Architectural Window on Left Wall (-X)
  const winW = Math.min(2.4, roomD * 0.45);
  const winH = 1.65;
  const winFrame = new THREE.Mesh(new THREE.BoxGeometry(0.18, winH + 0.12, winW + 0.12), matDarkNavy);
  winFrame.position.set(-roomW / 2 - 0.02, 1.55, -roomD * 0.08);
  roomGroup.add(winFrame);

  const winGlass = new THREE.Mesh(new THREE.BoxGeometry(0.06, winH, winW), matGlass);
  winGlass.position.set(-roomW / 2 + 0.03, 1.55, -roomD * 0.08);
  roomGroup.add(winGlass);

  // Framed Real Property Photograph on Back Wall
  const frameX = -roomW * 0.22;
  const frameOuter = new THREE.Mesh(new THREE.BoxGeometry(1.45, 0.96, 0.04), matDarkNavy);
  frameOuter.position.set(frameX, 1.85, -roomD / 2 + 0.03);
  roomGroup.add(frameOuter);

  const photoMat = new THREE.MeshBasicMaterial({ color: 0xe3e1dc });
  const photoCanvasMesh = new THREE.Mesh(new THREE.PlaneGeometry(1.33, 0.84), photoMat);
  photoCanvasMesh.position.set(frameX, 1.85, -roomD / 2 + 0.055);
  roomGroup.add(photoCanvasMesh);

  window.update3DWallPhoto = function (imgUrl) {
    if (!imgUrl) return;
    const loader = new THREE.TextureLoader();
    loader.setCrossOrigin('anonymous');
    loader.load(
      imgUrl,
      function (tex) {
        tex.encoding = THREE.sRGBEncoding;
        photoMat.map = tex;
        photoMat.color.setHex(0xffffff);
        photoMat.needsUpdate = true;
      },
      undefined,
      function () {
        if (imgUrl !== '/images/rooms/room-1.jpg') {
          loader.load('/images/rooms/room-1.jpg', function (fallbackTex) {
            fallbackTex.encoding = THREE.sRGBEncoding;
            photoMat.map = fallbackTex;
            photoMat.color.setHex(0xffffff);
            photoMat.needsUpdate = true;
          });
        }
      }
    );
  };
  const firstThumb = document.querySelector('.photo-strip-btn');
  if (firstThumb && firstThumb.getAttribute('data-img')) {
    window.update3DWallPhoto(firstThumb.getAttribute('data-img'));
  }

  // ============================================================================
  // 6. CONDITIONAL 3D FACILITIES SPAWNED STRICTLY FROM DATABASE (`p.amenities`)
  // ============================================================================

  const bedX = -roomW / 2 + 1.25;
  const bedZ = -roomD / 2 + 1.15;

  // [DB AMENITY 1]: "Giường nệm"
  if (hasBed) {
    const rugMesh = new THREE.Mesh(
      new THREE.BoxGeometry(2.4, 0.015, 2.2),
      new THREE.MeshStandardMaterial({ map: createRugTexture(), roughness: 0.9 })
    );
    rugMesh.position.set(bedX + 0.2, 0.045, bedZ + 0.15);
    rugMesh.receiveShadow = true;
    roomGroup.add(rugMesh);

    const bedGroup = new THREE.Group();
    bedGroup.position.set(bedX, 0.04, bedZ);

    const bedBase = new THREE.Mesh(new THREE.BoxGeometry(1.7, 0.28, 2.0), matOak);
    bedBase.position.y = 0.14;
    bedBase.castShadow = true;
    bedBase.receiveShadow = true;
    bedGroup.add(bedBase);

    const headboard = new THREE.Mesh(new THREE.BoxGeometry(1.7, 0.95, 0.12), matWalnut);
    headboard.position.set(0, 0.48, -0.94);
    headboard.castShadow = true;
    bedGroup.add(headboard);

    const mattress = new THREE.Mesh(new THREE.BoxGeometry(1.58, 0.22, 1.86), matWhiteFabric);
    mattress.position.set(0, 0.36, 0.02);
    mattress.castShadow = true;
    mattress.receiveShadow = true;
    bedGroup.add(mattress);

    const blanket = new THREE.Mesh(new THREE.BoxGeometry(1.6, 0.23, 1.15), matDarkNavy);
    blanket.position.set(0, 0.365, 0.38);
    blanket.castShadow = true;
    bedGroup.add(blanket);

    const accentThrow = new THREE.Mesh(new THREE.BoxGeometry(1.61, 0.24, 0.28), matAccentOrange);
    accentThrow.position.set(0, 0.37, 0.8);
    bedGroup.add(accentThrow);

    [-0.4, 0.4].forEach(px => {
      const pillow = new THREE.Mesh(new THREE.BoxGeometry(0.58, 0.12, 0.36), matWhiteFabric);
      pillow.position.set(px, 0.51, -0.62);
      pillow.rotation.x = 0.15;
      pillow.castShadow = true;
      bedGroup.add(pillow);
    });

    roomGroup.add(bedGroup);
    registerDBFacility(
      mattress,
      new THREE.Vector3(bedX, 0.78, bedZ),
      'Giường nệm',
      'Giường nệm',
      'Trang bị sẵn khung giường đôi gỗ sồi, đệm êm và bộ chăn gối tiêu chuẩn.',
      ['giường nệm', 'giường', 'nệm']
    );
  }

  // [DB AMENITY 2]: "Tủ quần áo"
  if (hasWardrobe) {
    const wardX = hasBed ? bedX + 1.95 : -roomW / 2 + 0.9;
    const wardrobe = new THREE.Mesh(new THREE.BoxGeometry(1.18, 2.15, 0.58), matOak);
    wardrobe.position.set(wardX, 1.11, -roomD / 2 + 0.31);
    wardrobe.castShadow = true;
    wardrobe.receiveShadow = true;
    roomGroup.add(wardrobe);

    // Door seam & handles
    const seam = new THREE.Mesh(new THREE.BoxGeometry(0.02, 2.05, 0.6), matWalnut);
    seam.position.set(wardX, 1.11, -roomD / 2 + 0.31);
    roomGroup.add(seam);

    registerDBFacility(
      wardrobe,
      new THREE.Vector3(wardX, 1.25, -roomD / 2 + 0.45),
      'Tủ quần áo',
      'Tủ quần áo',
      'Tủ gỗ 2 buồng lớn kịch trần giúp lưu trữ quần áo và hành lý gọn gàng.',
      ['tủ quần áo', 'tủ đồ', 'tủ']
    );
  }

  // [DB AMENITY 3]: "Điều hòa"
  if (hasAC) {
    const acX = 0.25;
    const acY = wallH - 0.45;
    const acZ = -roomD / 2 + 0.14;
    const acBody = new THREE.Mesh(new THREE.BoxGeometry(1.05, 0.3, 0.26), matWhiteFabric);
    acBody.position.set(acX, acY, acZ);
    acBody.castShadow = true;
    roomGroup.add(acBody);

    const acVent = new THREE.Mesh(new THREE.BoxGeometry(0.96, 0.05, 0.27), matDarkNavy);
    acVent.position.set(acX, acY - 0.1, acZ);
    roomGroup.add(acVent);

    const acLed = new THREE.Mesh(
      new THREE.BoxGeometry(0.08, 0.03, 0.28),
      new THREE.MeshStandardMaterial({ color: 0x38bdf8, emissive: 0x38bdf8, emissiveIntensity: 0.9 })
    );
    acLed.position.set(acX + 0.38, acY, acZ);
    roomGroup.add(acLed);

    registerDBFacility(
      acBody,
      new THREE.Vector3(acX, acY - 0.05, acZ + 0.15),
      'Điều hòa',
      'Điều hòa nhiệt độ',
      'Máy điều hòa Inverter lắp sẵn trên tường, công suất đáp ứng diện tích ' + rawAcreage + ' m².',
      ['điều hòa', 'máy lạnh']
    );
  }

  // [DB AMENITY 4]: "Kệ bếp"
  if (hasKitchen) {
    const kitchX = roomW / 2 - 1.05;
    const kitchZ = -roomD / 2 + 0.4;
    const kitchenCounter = new THREE.Mesh(new THREE.BoxGeometry(1.85, 0.88, 0.68), matWhiteFabric);
    kitchenCounter.position.set(kitchX, 0.46, kitchZ);
    kitchenCounter.castShadow = true;
    kitchenCounter.receiveShadow = true;
    roomGroup.add(kitchenCounter);

    const counterTop = new THREE.Mesh(new THREE.BoxGeometry(1.9, 0.05, 0.72), matWalnut);
    counterTop.position.set(kitchX, 0.91, kitchZ);
    roomGroup.add(counterTop);

    // Stainless Steel Sink & Faucet
    const sinkMesh = new THREE.Mesh(new THREE.BoxGeometry(0.55, 0.03, 0.42), matMetal);
    sinkMesh.position.set(kitchX - 0.42, 0.94, kitchZ);
    roomGroup.add(sinkMesh);

    const faucetStem = new THREE.Mesh(new THREE.CylinderGeometry(0.02, 0.02, 0.28, 12), matMetal);
    faucetStem.position.set(kitchX - 0.42, 1.08, kitchZ - 0.18);
    roomGroup.add(faucetStem);

    // Induction Stove
    const stoveMesh = new THREE.Mesh(
      new THREE.BoxGeometry(0.58, 0.03, 0.42),
      new THREE.MeshStandardMaterial({ color: 0x05070a, roughness: 0.2 })
    );
    stoveMesh.position.set(kitchX + 0.42, 0.94, kitchZ);
    roomGroup.add(stoveMesh);

    // Upper Hanging Kitchen Cabinet
    const upperCab = new THREE.Mesh(new THREE.BoxGeometry(1.85, 0.65, 0.38), matOak);
    upperCab.position.set(kitchX, 2.15, -roomD / 2 + 0.21);
    upperCab.castShadow = true;
    roomGroup.add(upperCab);

    registerDBFacility(
      kitchenCounter,
      new THREE.Vector3(kitchX, 1.15, kitchZ + 0.2),
      'Kệ bếp',
      'Kệ bếp nấu ăn riêng',
      'Kệ bếp mặt gỗ/đá chống bám bẩn, tích hợp bồn rửa inox, vòi nước và tủ bếp treo tường.',
      ['kệ bếp', 'bếp']
    );
  }

  // Bathroom / Wet Zone Coordinates (Front-Right Corner)
  const bathW = 1.85;
  const bathD = 1.75;
  const bathX = roomW / 2 - bathW / 2;
  const bathZ = roomD / 2 - bathD / 2;

  if (hasToiletInside || hasShower || hasWaterHeater) {
    // Wet-zone anti-slip tile floor & glass partition
    const bathTileFloor = new THREE.Mesh(
      new THREE.BoxGeometry(bathW, 0.05, bathD),
      new THREE.MeshStandardMaterial({ color: 0xdbe2ea, roughness: 0.35 })
    );
    bathTileFloor.position.set(bathX, 0.035, bathZ);
    bathTileFloor.receiveShadow = true;
    roomGroup.add(bathTileFloor);

    const bathBackPartition = new THREE.Mesh(new THREE.BoxGeometry(bathW, 2.25, 0.1), matWall);
    bathBackPartition.position.set(bathX, 1.14, bathZ - bathD / 2);
    bathBackPartition.castShadow = true;
    roomGroup.add(bathBackPartition);

    const bathSideGlass = new THREE.Mesh(new THREE.BoxGeometry(0.08, 2.25, bathD), matGlass);
    bathSideGlass.position.set(bathX - bathW / 2, 1.14, bathZ);
    roomGroup.add(bathSideGlass);
  }

  // [DB AMENITY 5]: "Bình nóng lạnh" (3D Water Heater Tank with Hot/Cold Pipes!)
  if (hasWaterHeater) {
    const whX = bathX + 0.15;
    const whY = 2.02;
    const whZ = bathZ - bathD / 2 + 0.22;

    const heaterTank = new THREE.Mesh(new THREE.BoxGeometry(0.68, 0.38, 0.32), matWhiteFabric);
    heaterTank.position.set(whX, whY, whZ);
    heaterTank.castShadow = true;
    roomGroup.add(heaterTank);

    const heaterPanel = new THREE.Mesh(new THREE.BoxGeometry(0.22, 0.12, 0.33), matDarkNavy);
    heaterPanel.position.set(whX, whY - 0.04, whZ);
    roomGroup.add(heaterPanel);

    const tempLed = new THREE.Mesh(
      new THREE.BoxGeometry(0.06, 0.04, 0.34),
      new THREE.MeshStandardMaterial({ color: 0xef4444, emissive: 0xef4444, emissiveIntensity: 0.9 })
    );
    tempLed.position.set(whX, whY - 0.04, whZ);
    roomGroup.add(tempLed);

    // Hot (Red) & Cold (Blue) Water Pipes underneath
    const hotPipe = new THREE.Mesh(
      new THREE.CylinderGeometry(0.018, 0.018, 0.35, 10),
      new THREE.MeshStandardMaterial({ color: 0xef4444 })
    );
    hotPipe.position.set(whX - 0.12, whY - 0.32, whZ - 0.05);
    roomGroup.add(hotPipe);

    const coldPipe = new THREE.Mesh(
      new THREE.CylinderGeometry(0.018, 0.018, 0.35, 10),
      new THREE.MeshStandardMaterial({ color: 0x3b82f6 })
    );
    coldPipe.position.set(whX + 0.12, whY - 0.32, whZ - 0.05);
    roomGroup.add(coldPipe);

    registerDBFacility(
      heaterTank,
      new THREE.Vector3(whX, whY, whZ + 0.15),
      'Bình nóng lạnh',
      'Bình nóng lạnh',
      'Bình đun nước nóng gián tiếp 30L có rơ-le chống giật ELCB, cấp nước nóng cho khu vệ sinh.',
      ['bình nóng lạnh', 'nóng lạnh']
    );
  }

  // [DB AMENITY 6]: "Vệ sinh trong" (3D Ceramic Toilet + Washbasin Vanity)
  if (hasToiletInside) {
    const wcX = bathX - 0.42;
    const wcZ = bathZ + 0.35;

    const toiletBowl = new THREE.Mesh(new THREE.BoxGeometry(0.42, 0.42, 0.56), matWhiteFabric);
    toiletBowl.position.set(wcX, 0.25, wcZ);
    toiletBowl.castShadow = true;
    roomGroup.add(toiletBowl);

    const toiletTank = new THREE.Mesh(new THREE.BoxGeometry(0.42, 0.45, 0.2), matWhiteFabric);
    toiletTank.position.set(wcX, 0.65, wcZ - 0.18);
    toiletTank.castShadow = true;
    roomGroup.add(toiletTank);

    registerDBFacility(
      toiletBowl,
      new THREE.Vector3(wcX, 0.75, wcZ),
      'Vệ sinh trong',
      'Vệ sinh khép kín',
      'Khu vệ sinh riêng biệt ngay bên trong phòng, trang bị thiết bị sứ vệ sinh cao cấp.',
      ['vệ sinh trong', 'wc', 'toilet']
    );
  }

  // [DB AMENITY 7]: "Phòng tắm" (3D Rain Shower Column & Glass Enclosure)
  if (hasShower) {
    const shX = bathX + 0.48;
    const shZ = bathZ - 0.25;

    const showerPipe = new THREE.Mesh(new THREE.CylinderGeometry(0.02, 0.02, 1.65, 12), matMetal);
    showerPipe.position.set(shX, 1.15, shZ);
    roomGroup.add(showerPipe);

    const showerHead = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.16, 0.03, 20), matMetal);
    showerHead.position.set(shX, 1.95, shZ + 0.15);
    roomGroup.add(showerHead);

    registerDBFacility(
      showerHead,
      new THREE.Vector3(shX, 1.55, shZ + 0.15),
      'Phòng tắm',
      'Phòng tắm đứng',
      'Khu tắm vòi sen cây riêng biệt với vách kính chắn nước và sàn lát gạch chống trơn.',
      ['phòng tắm', 'vòi sen', 'tắm']
    );
  }

  // [DB AMENITY 8]: "Wifi" (3D Dual-Antenna Wifi Router + Glowing Signal Indicator on Left Wall)
  if (hasWifi) {
    const wfX = -roomW / 2 + 0.22;
    const wfY = 1.95;
    const wfZ = roomD / 2 - 1.15;

    const routerShelf = new THREE.Mesh(new THREE.BoxGeometry(0.36, 0.04, 0.48), matOak);
    routerShelf.position.set(wfX, wfY - 0.04, wfZ);
    roomGroup.add(routerShelf);

    const routerBody = new THREE.Mesh(new THREE.BoxGeometry(0.24, 0.06, 0.34), matDarkNavy);
    routerBody.position.set(wfX, wfY, wfZ);
    routerBody.castShadow = true;
    roomGroup.add(routerBody);

    // 2 Vertical Antennas
    [-0.12, 0.12].forEach(az => {
      const ant = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, 0.26, 8), matDarkNavy);
      ant.position.set(wfX - 0.06, wfY + 0.14, wfZ + az);
      roomGroup.add(ant);
    });

    // Glowing Green Wifi Signal LED
    const wifiLed = new THREE.Mesh(
      new THREE.BoxGeometry(0.04, 0.03, 0.18),
      new THREE.MeshStandardMaterial({ color: 0x10b981, emissive: 0x10b981, emissiveIntensity: 1.0 })
    );
    wifiLed.position.set(wfX + 0.11, wfY + 0.01, wfZ);
    roomGroup.add(wifiLed);

    registerDBFacility(
      routerBody,
      new THREE.Vector3(wfX + 0.15, wfY + 0.15, wfZ),
      'Wifi',
      'Bộ phát Wifi tốc độ cao',
      'Trang bị sẵn bộ định tuyến Wifi băng tần kép phủ sóng toàn bộ diện tích phòng.',
      ['wifi', 'internet', 'mạng']
    );
  }

  // [DB AMENITY 9]: "Camera an ninh" (3D Security CCTV Camera at Upper Corner)
  if (hasCCTV) {
    const camX = -roomW / 2 + 0.25;
    const camY = wallH - 0.28;
    const camZ = roomD / 2 - 0.28;

    const camMount = new THREE.Mesh(new THREE.BoxGeometry(0.18, 0.12, 0.12), matDarkNavy);
    camMount.position.set(camX, camY, camZ);
    roomGroup.add(camMount);

    const camHousing = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.09, 0.28, 16), matWhiteFabric);
    camHousing.rotation.z = Math.PI / 3;
    camHousing.rotation.y = -Math.PI / 4;
    camHousing.position.set(camX + 0.12, camY - 0.04, camZ - 0.08);
    roomGroup.add(camHousing);

    const camRecLed = new THREE.Mesh(
      new THREE.SphereGeometry(0.025, 10, 10),
      new THREE.MeshStandardMaterial({ color: 0xef4444, emissive: 0xef4444, emissiveIntensity: 1.0 })
    );
    camRecLed.position.set(camX + 0.2, camY - 0.08, camZ - 0.14);
    roomGroup.add(camRecLed);

    registerDBFacility(
      camHousing,
      new THREE.Vector3(camX + 0.15, camY - 0.1, camZ - 0.1),
      'Camera an ninh',
      'Camera an ninh 24/7',
      'Hệ thống camera giám sát khu vực cửa ra vào và hành lang chung hoạt động 24/7.',
      ['camera', 'an ninh']
    );
  }

  // [DB AMENITY 10]: "Bãi để xe riêng" (Exterior Covered Motorbike Parking Zone next to Room)
  if (hasParking) {
    const parkW = 2.2;
    const parkD = 2.6;
    const parkX = -roomW / 2 - parkW / 2 - 0.35;
    const parkZ = roomD / 2 - parkD / 2;

    const parkPad = new THREE.Mesh(
      new THREE.BoxGeometry(parkW, 0.08, parkD),
      new THREE.MeshStandardMaterial({ color: 0x334155, roughness: 0.85 })
    );
    parkPad.position.set(parkX, -0.04, parkZ);
    parkPad.receiveShadow = true;
    roomGroup.add(parkPad);

    // Parking Bay Stripes & Canopy
    const stripe = new THREE.Mesh(new THREE.BoxGeometry(1.6, 0.09, 0.06), matAccentOrange);
    stripe.position.set(parkX, -0.03, parkZ);
    roomGroup.add(stripe);

    // Stylized Scooter / Motorbike Model in Parking Bay
    const bikeBody = new THREE.Mesh(new THREE.BoxGeometry(1.15, 0.45, 0.38), matDarkNavy);
    bikeBody.position.set(parkX, 0.36, parkZ - 0.45);
    bikeBody.castShadow = true;
    roomGroup.add(bikeBody);

    const bikeSeat = new THREE.Mesh(new THREE.BoxGeometry(0.55, 0.12, 0.36), matWalnut);
    bikeSeat.position.set(parkX - 0.15, 0.62, parkZ - 0.45);
    roomGroup.add(bikeSeat);

    registerDBFacility(
      bikeBody,
      new THREE.Vector3(parkX, 0.75, parkZ - 0.45),
      'Bãi để xe riêng',
      'Bãi để xe riêng',
      'Khu vực để xe máy riêng rộng rãi, có vạch phân làn và lối đi thuận tiện.',
      ['bãi để xe', 'để xe', 'xe máy']
    );
  }

  // Catch-all for any custom amenity in `p.amenities` not covered by the 10 standard ones
  dbAmenities.forEach((amenName, idx) => {
    const lower = amenName.toLowerCase();
    const alreadyMapped = hotspotData.some(h => h.keywords.some(kw => lower.indexOf(kw) !== -1));
    if (!alreadyMapped) {
      const customX = -0.8 + (idx % 3) * 0.85;
      const customZ = 0.25;
      const customMesh = new THREE.Mesh(new THREE.BoxGeometry(0.55, 0.55, 0.55), matAccentOrange);
      customMesh.position.set(customX, 0.32, customZ);
      customMesh.castShadow = true;
      roomGroup.add(customMesh);
      registerDBFacility(
        customMesh,
        new THREE.Vector3(customX, 0.75, customZ),
        amenName,
        amenName,
        'Hạng mục tiện ích có sẵn của phòng.',
        [lower]
      );
    }
  });

  // 7. Lighting System (Day / Sunset / Night)
  const hemiLight = new THREE.HemisphereLight(0xffffff, 0x334155, 0.85);
  hemiLight.position.set(0, 10, 0);
  scene.add(hemiLight);

  const sunLight = new THREE.DirectionalLight(0xfffbeb, 1.15);
  sunLight.position.set(-7, 9, 5);
  sunLight.castShadow = true;
  sunLight.shadow.mapSize.width = 2048;
  sunLight.shadow.mapSize.height = 2048;
  sunLight.shadow.camera.near = 0.5;
  sunLight.shadow.camera.far = 28;
  const d = 7.0;
  sunLight.shadow.camera.left = -d;
  sunLight.shadow.camera.right = d;
  sunLight.shadow.camera.top = d;
  sunLight.shadow.camera.bottom = -d;
  sunLight.shadow.bias = -0.0005;
  scene.add(sunLight);

  const warmRoomLight = new THREE.PointLight(0xffb74d, 0.45, 10);
  warmRoomLight.position.set(0, 2.2, 0);
  scene.add(warmRoomLight);

  window.set3DLighting = function (mode) {
    document.querySelectorAll('.light-preset-btn').forEach(btn => {
      const active = btn.getAttribute('data-light') === mode;
      btn.className = active
        ? 'light-preset-btn px-2.5 py-1 bg-[#146c38] text-white small font-medium rounded border-0 cursor-pointer'
        : 'light-preset-btn px-2.5 py-1 bg-transparent text-white hover:bg-[#146c38] small font-medium rounded border-0 cursor-pointer';
    });

    if (mode === 'day') {
      scene.background.setHex(0x0b101e);
      scene.fog.color.setHex(0x0b101e);
      hemiLight.intensity = 0.88;
      sunLight.color.setHex(0xfffbeb);
      sunLight.intensity = 1.15;
      sunLight.position.set(-7, 9, 5);
      warmRoomLight.intensity = 0.35;
    } else if (mode === 'sunset') {
      scene.background.setHex(0x181224);
      scene.fog.color.setHex(0x181224);
      hemiLight.intensity = 0.55;
      sunLight.color.setHex(0xf97316);
      sunLight.intensity = 1.35;
      sunLight.position.set(-9, 4.2, 3);
      warmRoomLight.intensity = 0.95;
    } else if (mode === 'night') {
      scene.background.setHex(0x05070a);
      scene.fog.color.setHex(0x05070a);
      hemiLight.intensity = 0.22;
      sunLight.color.setHex(0x60a5fa);
      sunLight.intensity = 0.25;
      sunLight.position.set(-5, 8, -4);
      warmRoomLight.intensity = 1.55;
    }
  };

  // 8. Build Projected 3D Hotspot Elements for Each DB Facility
  let hotspotsVisible = true;
  const hotspotElements = hotspotData.map((h) => {
    const el = document.createElement('button');
    el.type = 'button';
    el.className =
      'pointer-events-auto absolute -translate-x-1/2 -translate-y-1/2 px-2.5 py-1 bg-[#111814]/90 hover:bg-[#146c38] text-white text-sm font-medium flex items-center gap-1.5 transition-colors cursor-pointer whitespace-nowrap rounded';
    el.innerHTML = '<span class="w-2 h-2 rounded-full bg-[#4ade80]"></span><span>' + h.label + '</span>';
    el.addEventListener('click', (e) => {
      e.stopPropagation();
      document.getElementById('inspectorTitle').textContent = h.title;
      document.getElementById('inspectorDesc').textContent = h.desc;
      desiredTarget.copy(h.pos);
      desiredSpherical.radius = 4.6;
    });
    hotspotsLayer.appendChild(el);
    return { data: h, el: el };
  });

  window.focus3DAmenityByName = function (amenName) {
    if (!amenName) return;
    const modalEl = document.getElementById('property3dModal');
    if (modalEl && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
      const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
      modal.show();
      setTimeout(function() {
        if (window.onThreeResize) window.onThreeResize();
      }, 250);
    }
    const q = amenName.toLowerCase();
    const found = hotspotData.find(h => h.keywords.some(kw => q.indexOf(kw) !== -1 || kw.indexOf(q) !== -1));
    if (found) {
      autoRotate = false;
      const titleEl = document.getElementById('inspectorTitle');
      if (titleEl) titleEl.textContent = found.title;
      const descEl = document.getElementById('inspectorDesc');
      if (descEl) descEl.textContent = found.desc;
      desiredTarget.copy(found.pos);
      desiredSpherical.radius = 4.5;
    }
  };

  window.toggle3DHotspots = function () {
    hotspotsVisible = !hotspotsVisible;
    hotspotsLayer.style.display = hotspotsVisible ? 'block' : 'none';
  };

  // 9. Smooth Spherical Camera Controller
  let spherical = { theta: 0.72, phi: 1.04, radius: Math.max(7.6, roomW * 1.38) };
  let desiredSpherical = { theta: 0.72, phi: 1.04, radius: Math.max(7.6, roomW * 1.38) };
  const cameraTarget = new THREE.Vector3(0, 0.75, 0);
  const desiredTarget = new THREE.Vector3(0, 0.75, 0);
  let autoRotate = true;
  let isDragging = false;
  let prevMouse = { x: 0, y: 0 };

  window.set3DCameraPreset = function (preset) {
    document.querySelectorAll('.cam-preset-btn').forEach(btn => {
      const active = btn.getAttribute('data-cam') === preset;
      btn.className = active
        ? 'cam-preset-btn px-3 py-1.5 bg-[#146c38] text-white small font-semibold rounded border-0 cursor-pointer'
        : 'cam-preset-btn px-3 py-1.5 bg-transparent text-white hover:bg-[#146c38] small font-medium rounded border-0 cursor-pointer';
    });

    if (preset === 'isometric') {
      desiredSpherical = { theta: 0.72, phi: 1.04, radius: Math.max(7.6, roomW * 1.38) };
      desiredTarget.set(0, 0.75, 0);
    } else if (preset === 'top') {
      desiredSpherical = { theta: 0.01, phi: 0.14, radius: Math.max(8.6, roomW * 1.55) };
      desiredTarget.set(0, 0.2, 0);
    } else if (preset === 'interior') {
      desiredSpherical = { theta: 0.38, phi: 1.24, radius: 4.8 };
      desiredTarget.set(0, 0.85, -roomD * 0.15);
    }
  };

  window.zoom3D = function (delta) {
    desiredSpherical.radius = Math.max(3.2, Math.min(15.5, desiredSpherical.radius + delta));
  };

  window.toggle3DAutoRotate = function () {
    autoRotate = !autoRotate;
    const btn = document.getElementById('btnToggleAutoRotate');
    if (btn) {
      btn.className = autoRotate
        ? 'px-3 py-1.5 bg-[#146c38] text-white small font-semibold rounded border-0 cursor-pointer'
        : 'px-3 py-1.5 bg-black hover:bg-[#146c38] text-white small font-medium rounded border-0 cursor-pointer';
    }
  };

  window.toggle3DFullscreen = function () {
    const container = document.getElementById('panel3DContainer');
    if (!document.fullscreenElement) {
      if (container.requestFullscreen) container.requestFullscreen();
    } else {
      if (document.exitFullscreen) document.exitFullscreen();
    }
  };

  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  let downPos = { x: 0, y: 0 };

  renderer.domElement.addEventListener('pointerdown', (e) => {
    isDragging = true;
    autoRotate = false;
    prevMouse = { x: e.clientX, y: e.clientY };
    downPos = { x: e.clientX, y: e.clientY };
  });

  window.addEventListener('pointermove', (e) => {
    if (!isDragging) return;
    const dx = e.clientX - prevMouse.x;
    const dy = e.clientY - prevMouse.y;
    prevMouse = { x: e.clientX, y: e.clientY };
    desiredSpherical.theta -= dx * 0.0065;
    desiredSpherical.phi = Math.max(0.12, Math.min(1.48, desiredSpherical.phi - dy * 0.0055));
  });

  window.addEventListener('pointerup', (e) => {
    if (!isDragging) return;
    isDragging = false;
    const dist = Math.hypot(e.clientX - downPos.x, e.clientY - downPos.y);
    if (dist < 6) {
      const rect = renderer.domElement.getBoundingClientRect();
      pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
      pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
      raycaster.setFromCamera(pointer, camera);
      const hits = raycaster.intersectObjects(interactables, false);
      if (hits.length > 0 && hits[0].object.userData) {
        const info = hits[0].object.userData;
        document.getElementById('inspectorTitle').textContent = info.title;
        document.getElementById('inspectorDesc').textContent = info.desc;
      }
    }
  });

  window.onThreeResize = function () {
    if (!mount.clientWidth || !mount.clientHeight) return;
    camera.aspect = mount.clientWidth / mount.clientHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(mount.clientWidth, mount.clientHeight);
  };
  window.addEventListener('resize', window.onThreeResize);
  document.addEventListener('fullscreenchange', () => setTimeout(window.onThreeResize, 100));

  function animate() {
    requestAnimationFrame(animate);

    if (autoRotate && !isDragging) {
      desiredSpherical.theta += 0.0018;
    }

    spherical.theta += (desiredSpherical.theta - spherical.theta) * 0.08;
    spherical.phi += (desiredSpherical.phi - spherical.phi) * 0.08;
    spherical.radius += (desiredSpherical.radius - spherical.radius) * 0.08;
    cameraTarget.lerp(desiredTarget, 0.08);

    const sinPhiRadius = Math.sin(spherical.phi) * spherical.radius;
    camera.position.x = cameraTarget.x + sinPhiRadius * Math.sin(spherical.theta);
    camera.position.y = cameraTarget.y + Math.cos(spherical.phi) * spherical.radius;
    camera.position.z = cameraTarget.z + sinPhiRadius * Math.cos(spherical.theta);
    camera.lookAt(cameraTarget);

    if (hotspotsVisible && mount.clientWidth > 0) {
      const w = mount.clientWidth;
      const h = mount.clientHeight;
      hotspotElements.forEach(item => {
        const projected = item.data.pos.clone().project(camera);
        if (projected.z > 1) {
          item.el.style.display = 'none';
        } else {
          item.el.style.display = 'flex';
          const x = (projected.x * 0.5 + 0.5) * w;
          const y = (-projected.y * 0.5 + 0.5) * h;
          item.el.style.left = x + 'px';
          item.el.style.top = y + 'px';
        }
      });
    }

    renderer.render(scene, camera);
  }
  animate();
})();

/* ── Smart Monthly Cost Calculator ── */
let currentOccupants = 1;

function setOccupants(num) {
  currentOccupants = num;
  document.querySelectorAll('.occ-btn').forEach(btn => {
    if (parseInt(btn.getAttribute('data-occ'), 10) === num) {
      btn.className = 'occ-btn px-3 py-1.5 small font-semibold bg-[#146c38] text-white border-0 cursor-pointer';
    } else {
      btn.className = 'occ-btn px-3 py-1.5 small font-semibold text-[#1b1b1b] bg-transparent border-0 cursor-pointer';
    }
  });
  calculateMonthlyCost();
}

function calculateMonthlyCost() {
  const card = document.getElementById('costCalculatorCard');
  if (!card) return;
  const basePrice = parseFloat(card.getAttribute('data-base-price')) || 0;
  const elecKwh = parseFloat(document.getElementById('calcElecKwh').value) || 0;
  const waterM3 = parseFloat(document.getElementById('calcWaterM3').value) || 0;
  const serviceFee = parseFloat(document.getElementById('calcServiceFee').value) || 0;

  const elecCost = elecKwh * 3500;
  const waterCost = waterM3 * 20000;
  const grandTotal = basePrice + elecCost + waterCost + serviceFee;
  const perPerson = Math.round(grandTotal / (currentOccupants || 1));

  document.getElementById('calcElecKwhLabel').textContent = elecKwh;
  document.getElementById('calcElecTotalLabel').textContent = elecCost.toLocaleString('vi-VN') + ' đ';
  document.getElementById('calcWaterM3Label').textContent = waterM3;
  document.getElementById('calcWaterTotalLabel').textContent = waterCost.toLocaleString('vi-VN') + ' đ';
  document.getElementById('calcGrandTotal').textContent = grandTotal.toLocaleString('vi-VN') + ' đ';
  document.getElementById('calcPerPerson').textContent = perPerson.toLocaleString('vi-VN') + ' đ/người';
}

function fillQuickInquiryNote(text) {
  const noteEl = document.getElementById('contactNote');
  if (noteEl) {
    noteEl.value = text;
    noteEl.focus();
  }
}

let currentDayNightToggle = 'day';
function toggle3DDayNightLighting() {
  currentDayNightToggle = currentDayNightToggle === 'day' ? 'night' : 'day';
  if (window.set3DLighting) {
    window.set3DLighting(currentDayNightToggle);
  }
  const lbl = document.getElementById('dayNightToggleLabel');
  if (lbl) {
    lbl.textContent = currentDayNightToggle === 'day'
      ? 'Ánh sáng Ngày / Đêm (Đang: Ngày)'
      : 'Ánh sáng Ngày / Đêm (Đang: Đêm)';
  }
}

function autoEstimateByRoomAppliances() {
  const elecInput = document.getElementById('calcElecKwh');
  const waterInput = document.getElementById('calcWaterM3');
  if (elecInput) elecInput.value = 120;
  if (waterInput) waterInput.value = 6;
  calculateMonthlyCost();
}

function submitPropertyReport(reason) {
  const toast = document.getElementById('reportListingToast');
  const toastTxt = document.getElementById('reportListingToastText');
  const optsBox = document.getElementById('reportOptionsBox');
  if (optsBox) optsBox.classList.add('hidden');
  if (toastTxt && reason) {
    toastTxt.textContent = 'Đã ghi nhận báo cáo ("' + reason + '"). Bộ phận kiểm duyệt sẽ xác minh trong 24h.';
  }
  if (toast) toast.classList.remove('hidden');
}

function recordRecentlyViewedProperty() {
  const showcase = document.getElementById('property3dShowcase');
  if (!showcase) return;
  const slug = (showcase.getAttribute('data-slug') || '').trim();
  if (!slug) return;
  const title = showcase.getAttribute('data-title') || 'Không gian lưu trú';
  const price = showcase.getAttribute('data-price') || 0;
  const address = showcase.getAttribute('data-address') || '';
  const firstPhotoEl = document.querySelector('.photo-strip-btn');
  const mainImgEl = document.getElementById('mainGalleryImage');
  const image = (firstPhotoEl && firstPhotoEl.getAttribute('data-img')) || (mainImgEl && mainImgEl.getAttribute('src')) || '/images/rooms/room-1.jpg';

  try {
    const raw = localStorage.getItem('tm_recently_viewed');
    let list = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(list)) list = [];
    list = list.filter(function (item) {
      return item && item.slug && item.slug !== slug;
    });
    list.unshift({
      slug: slug,
      title: title,
      price: price,
      address: address,
      image: image,
      viewedAt: Date.now()
    });
    list = list.slice(0, 4);
    localStorage.setItem('tm_recently_viewed', JSON.stringify(list));
  } catch (e) {}
}

document.addEventListener('DOMContentLoaded', function () {
  calculateMonthlyCost();
  recordRecentlyViewedProperty();
});
calculateMonthlyCost();
recordRecentlyViewedProperty();
