import Photos

@objc(DownloadManager) class DownloadManager : CDVPlugin , UIDocumentInteractionControllerDelegate {

    @objc(download:)
    func download(_ command: CDVInvokedUrlCommand){
        let url = command.arguments[0] as? String ?? ""
        let fileName = command.arguments[1] as? String ?? ""

        if let downloadURL = getURLFromString(url) {
            let downloadProgress =  DownloadUrlProgress(callback: self, callbackId: command.callbackId, fileName: fileName)
            downloadProgress.download(from: downloadURL)
        }
    }

    @objc(open:)
    func open(_ command: CDVInvokedUrlCommand){
        let path = command.arguments[0] as? String ?? ""
        NSLog(path)
        let url = URL(fileURLWithPath: path)
        let documentInteractionController = UIDocumentInteractionController(url: url)
        documentInteractionController.delegate = self
        documentInteractionController.presentPreview(animated: false)
    }

    func documentInteractionControllerViewControllerForPreview(_: UIDocumentInteractionController) -> UIViewController {
        if #available(iOS 15.0, *) {
            let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene
            let keyWindow = scene?.keyWindow
            return keyWindow!.rootViewController!
        } else {
            return UIApplication.shared.windows.first!.rootViewController!
        }
    }

    func getURLFromString(_ str: String) -> URL? {
        return URL(string: str)
    }

    class DownloadUrlProgress : NSObject, URLSessionDownloadDelegate {
        var _callback : CDVPlugin
        var _callbackId : String
        var _fileName : String
        internal let mimeTypes = [
            "html": "text/html",
            "htm": "text/html",
            "shtml": "text/html",
            "css": "text/css",
            "xml": "text/xml",
            "gif": "image/gif",
            "jpeg": "image/jpeg",
            "jpg": "image/jpeg",
            "js": "application/javascript",
            "atom": "application/atom+xml",
            "rss": "application/rss+xml",
            "mml": "text/mathml",
            "txt": "text/plain",
            "jad": "text/vnd.sun.j2me.app-descriptor",
            "wml": "text/vnd.wap.wml",
            "htc": "text/x-component",
            "png": "image/png",
            "tif": "image/tiff",
            "tiff": "image/tiff",
            "wbmp": "image/vnd.wap.wbmp",
            "ico": "image/x-icon",
            "jng": "image/x-jng",
            "bmp": "image/x-ms-bmp",
            "svg": "image/svg+xml",
            "svgz": "image/svg+xml",
            "webp": "image/webp",
            "woff": "application/font-woff",
            "jar": "application/java-archive",
            "war": "application/java-archive",
            "ear": "application/java-archive",
            "json": "application/json",
            "hqx": "application/mac-binhex40",
            "doc": "application/msword",
            "pdf": "application/pdf",
            "ps": "application/postscript",
            "eps": "application/postscript",
            "ai": "application/postscript",
            "rtf": "application/rtf",
            "m3u8": "application/vnd.apple.mpegurl",
            "xls": "application/vnd.ms-excel",
            "eot": "application/vnd.ms-fontobject",
            "ppt": "application/vnd.ms-powerpoint",
            "wmlc": "application/vnd.wap.wmlc",
            "kml": "application/vnd.google-earth.kml+xml",
            "kmz": "application/vnd.google-earth.kmz",
            "7z": "application/x-7z-compressed",
            "cco": "application/x-cocoa",
            "jardiff": "application/x-java-archive-diff",
            "jnlp": "application/x-java-jnlp-file",
            "run": "application/x-makeself",
            "pl": "application/x-perl",
            "pm": "application/x-perl",
            "prc": "application/x-pilot",
            "pdb": "application/x-pilot",
            "rar": "application/x-rar-compressed",
            "rpm": "application/x-redhat-package-manager",
            "sea": "application/x-sea",
            "swf": "application/x-shockwave-flash",
            "sit": "application/x-stuffit",
            "tcl": "application/x-tcl",
            "tk": "application/x-tcl",
            "der": "application/x-x509-ca-cert",
            "pem": "application/x-x509-ca-cert",
            "crt": "application/x-x509-ca-cert",
            "xpi": "application/x-xpinstall",
            "xhtml": "application/xhtml+xml",
            "xspf": "application/xspf+xml",
            "zip": "application/zip",
            "epub": "application/epub+zip",
            "docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "mid": "audio/midi",
            "midi": "audio/midi",
            "kar": "audio/midi",
            "mp3": "audio/mpeg",
            "ogg": "audio/ogg",
            "m4a": "audio/x-m4a",
            "ra": "audio/x-realaudio",
            "3gpp": "video/3gpp",
            "3gp": "video/3gpp",
            "ts": "video/mp2t",
            "mp4": "video/mp4",
            "mpeg": "video/mpeg",
            "mpg": "video/mpeg",
            "mov": "video/quicktime",
            "webm": "video/webm",
            "flv": "video/x-flv",
            "m4v": "video/x-m4v",
            "mng": "video/x-mng",
            "asx": "video/x-ms-asf",
            "asf": "video/x-ms-asf",
            "wmv": "video/x-ms-wmv",
            "avi": "video/x-msvideo"
        ]
        init(callback: CDVPlugin, callbackId: String, fileName: String){
            self._callback = callback;
            self._callbackId = callbackId;
            self._fileName = fileName;
        }


        public func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {

            let destination = makeExtensionFile(downloadTask: downloadTask, location: location)
            let mimeType = getMimeType(url: destination)
            if (mimeType != nil) {
                if mimeType!.starts(with: "image") {
                    saveToPhotos(createRequest: { url in
                        PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: url)
                    }, url: destination)
                    return
                }
                if mimeType!.starts(with: "video") {
                    saveToPhotos(createRequest: { url in
                        PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: url)
                    }, url: destination)
                    return
                }
            }
            saveToDocument(source: destination, fileName: self._fileName)
        }

        func saveToPhotos(createRequest: @escaping (URL) -> PHAssetChangeRequest?, url: URL){
            var placeHolder: PHObjectPlaceholder? = nil
            PHPhotoLibrary.shared().performChanges({
                let request = createRequest(url)
                placeHolder = request?.placeholderForCreatedAsset
            }) {(saved,err) in
                if let localIdentifier = placeHolder?.localIdentifier, saved {
                    NSLog("Saved to photo %s",localIdentifier)
                    let responseMessages = ["url": localIdentifier]
                    let pluginResult = CDVPluginResult(
                        status: CDVCommandStatus_OK,
                        messageAs: responseMessages
                    )
                    pluginResult?.setKeepCallbackAs(true)
                    self._callback.commandDelegate!.send(
                        pluginResult,
                        callbackId: self._callbackId
                    )
                }
            }
        }

        func saveToDocument(source: URL, fileName: String) {
            do {
                let fileURL = try getFileURL(fileName: fileName)

                try FileManager.default.moveItem(at: source, to: fileURL)
                let responseMessages = ["url": fileURL.path()]
                let pluginResult = CDVPluginResult(
                    status: CDVCommandStatus_OK,
                    messageAs: responseMessages
                )
                self._callback.commandDelegate!.send(
                    pluginResult,
                    callbackId: self._callbackId
                )
                NSLog("Saved file")
            }
            catch {
                NSLog("Error %s",error.localizedDescription)
            }
        }

        func getFileURL(fileName : String) throws -> URL  {
            let documentDirectory = try FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor:nil, create:false)
            var fileURL = documentDirectory.appendingPathComponent(fileName)

            var name = fileName
            var ext = "";

            if let dotRange = fileName.range(of: "."){
                name = String(fileName[..<dotRange.lowerBound])
                ext = String(fileName[dotRange.upperBound...])
            }

            var counter = 1
            while FileManager.default.fileExists(atPath: fileURL.path) {
                let newFilename = "\(name)(\(counter)).\(ext)"
                fileURL = documentDirectory.appendingPathComponent(newFilename)
                counter += 1
            }
            return fileURL
        }

        func getMimeType(url : URL) -> String? {
            return mimeTypes[url.pathExtension]
        }

        func makeExtensionFile(downloadTask: URLSessionDownloadTask, location: URL) -> URL {
            do {
                let originalURL = downloadTask.originalRequest?.url
                let fileName = location.lastPathComponent.replacing(".tmp", with: "")
                // Check if original URL has extension
                if let originalExtension = originalURL?.pathExtension, !originalExtension.isEmpty {
                    // Replace extension with the original one
                    let destinationURL = location.deletingLastPathComponent().appendingPathComponent(fileName + "." + originalExtension)
                    try FileManager.default.moveItem(at: location, to: destinationURL)
                    return destinationURL;
                } else {
                    return location;
                }
            } catch {
                return location;
            }
        }

        func download(from url: URL) {
            let configuration = URLSessionConfiguration.default
            let operationQueue = OperationQueue()
            let session = URLSession(configuration: configuration, delegate: self, delegateQueue: operationQueue)

            let downloadTask = session.downloadTask(with: url)
            downloadTask.resume()
        }

        public func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
            let percentDownloaded = Float(totalBytesWritten) / Float(totalBytesExpectedToWrite)
            let responseMessages = ["percent": percentDownloaded]

            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_OK,
                messageAs: responseMessages
            )
            pluginResult?.setKeepCallbackAs(true)
            self._callback.commandDelegate!.send(
                pluginResult,
                callbackId: self._callbackId
            )
        }
    }

}
