var app = new Vue({
  el: '#app',
  data: {
    sharedFiles:[],
    stompClient: null,
    location: document.location
  },
  beforeMount() {
    this.connect();
  },
  methods: {
    connect() {
        let socket = new SockJS('/gs-guide-websocket');
        this.stompClient = Stomp.over(socket);
        this.stompClient.connect({}, frame => {
            console.log('Connected: ' + frame);
        });
    },
    upload(event) {
        let file = event.target.files[0];
        let data = new FormData();
        data.append('file', file);
        fetch('/file/share', {
          method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                 filename: file.name,
                 size: file.size
            })
        })
        .then(response => response.json())
        .then(data => {
            this.$refs.fileUpload.value=null;

            let item = {
                filename: file.name,
                size: file.size,
                hash: data.shareHash,
                blob: file,
                downloads: []
            };
            this.sharedFiles.push(item);

            this.stompClient.subscribe('/topic/' + data.shareHash, response => {
                let jsonResp = JSON.parse(response.body);
                let download = {
                    ip: jsonResp.ip,
                    progress: 0
                };

                item.downloads.push(download);

                let data = new FormData();
                data.append('file', file);

                let request = new XMLHttpRequest();
                request.open('POST', '/file/upload/' + jsonResp.shareHash + '/' + jsonResp.streamHash);

                // upload progress event
                request.upload.addEventListener('progress', e => {
                   download.progress = (e.loaded / e.total) * 100;
                });

                // request finished event
                request.addEventListener('load', e => {

                });

                // send POST request to server
                request.send(data);
            }, {
                id: data.shareHash
            });
        });
    },
    remove(item) {
        fetch('/file/unshare', {
          method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                 shareHash: item.hash
            })
        })
        .then(resp => {
            this.sharedFiles = this.sharedFiles.filter(x => x.hash !== item.hash);
            this.stompClient.unsubscribe(item.hash);
        });
    },
    copy(item) {
        let copyText = document.getElementById("link-" + item.hash);

        /* Select the text field */
        copyText.select();
        copyText.setSelectionRange(0, 99999); /*For mobile devices*/

        /* Copy the text inside the text field */
        document.execCommand("copy");
    },
    size(blob) {
        let size = blob.size;
        if(size < 1024) {
            return size.toFixed(1) + " B";
        }

        size /= 1024;
        if(size < 1024) {
            return size.toFixed(1) + " KB";
        }

        size /= 1024;
        if(size < 1024) {
            return size.toFixed(1) + " MB";
        }

        size /= 1024;
        if(size < 1024) {
            return size.toFixed(1) + " GB";
        }

        size /= 1024;
        if(size < 1024) {
            return size.toFixed(1) + " TB";
        }
    }
  }
});