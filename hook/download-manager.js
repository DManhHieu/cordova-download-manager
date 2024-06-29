function DownloadManager() {
}

DownloadManager.prototype = {
  download: function(url, fileName, description, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'DownloadManager', 'download', [url, fileName, description]);
  },
  open: function(url, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'DownloadManager', 'open', [url]);
  }
};

module.exports = new DownloadManager();
