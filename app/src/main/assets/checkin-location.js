(function () {
  var bridge = window.XatuCheckinLocation;
  if (!bridge || window.__xatuCheckinInstalled) return;
  window.__xatuCheckinInstalled = true;
  var geo = navigator.geolocation;
  if (!geo) return;
  var nativeGet = geo.getCurrentPosition.bind(geo);
  var nativeQuery = navigator.permissions && navigator.permissions.query && navigator.permissions.query.bind(navigator.permissions);
  var sequence = 0, pending = {}, watches = {};
  bridge.onmessage = function (event) {
    var value;
    try { value = JSON.parse(event.data); } catch (_) { return; }
    var callback = pending[value.id];
    delete pending[value.id];
    if (callback) callback(value);
  };
  function probe(callback) {
    var id = ++sequence;
    pending[id] = callback;
    bridge.postMessage(JSON.stringify({id: id}));
  }
  function position(reply) {
    return {coords: {latitude: reply.latitude, longitude: reply.longitude, accuracy: 10,
      altitude: null, altitudeAccuracy: null, heading: null, speed: null}, timestamp: Date.now()};
  }
  function failure(callback, reply) {
    if (callback) callback({code: 2, message: reply.message || '请先选择有效的签到位置',
      PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3});
  }
  function locate(success, error, options, active) {
    probe(function (reply) {
      if (!active()) return;
      if (reply.mode === 'custom') { if (success) success(position(reply)); return; }
      if (reply.mode === 'error') { failure(error, reply); return; }
      nativeGet(function (result) {
        // Re-check the selected source if it changes while a real GPS request is pending.
        probe(function (latest) {
          if (!active()) return;
          if (latest.mode === 'custom') { if (success) success(position(latest)); }
          else if (latest.mode === 'error') failure(error, latest);
          else if (success) success(result);
        });
      }, function (reason) {
        probe(function (latest) {
          if (!active()) return;
          if (latest.mode === 'custom') { if (success) success(position(latest)); }
          else if (latest.mode === 'error') failure(error, latest);
          else if (error) error(reason);
        });
      }, options);
    });
  }
  geo.getCurrentPosition = function (success, error, options) { locate(success, error, options, function () { return true; }); };
  geo.watchPosition = function (success, error, options) {
    var id = ++sequence;
    watches[id] = {timer: null};
    function tick() { locate(success, error, options, function () { return !!watches[id]; }); }
    watches[id].timer = setInterval(tick, 3000);
    tick();
    return id;
  };
  geo.clearWatch = function (id) {
    if (watches[id]) { clearInterval(watches[id].timer); delete watches[id]; }
  };
  if (nativeQuery) navigator.permissions.query = function (descriptor) {
    if (!descriptor || descriptor.name !== 'geolocation') return nativeQuery(descriptor);
    return new Promise(function (resolve, reject) {
      probe(function (reply) {
        if (reply.mode === 'custom') resolve({state: 'granted', onchange: null});
        else nativeQuery(descriptor).then(resolve, reject);
      });
    });
  };
  window.addEventListener('pagehide', function () {
    Object.keys(watches).forEach(function (id) { geo.clearWatch(id); });
    pending = {};
  });
})();
