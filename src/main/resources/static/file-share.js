var app = new Vue({
  el: '#app',
  data: {
    sharedFiles:[],
    stompClient: null,
    location: document.location,
    dropzoneOptions: {
              url: 'https://httpbin.org/post',
              thumbnailWidth: 150,
              maxFilesize: 0.5,
              headers: { "My-Awesome-Header": "header value" }
          }
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
            this.sharedFiles.push({
                filename: file.name,
                size: file.size,
                hash: data.shareHash,
                blob: file
            });

            this.stompClient.subscribe('/topic/' + data.shareHash, response => {
                let jsonResp = JSON.parse(response.body);

                let data = new FormData();
                data.append('file', file);

                fetch('/file/upload/' + jsonResp.shareHash + '/' + jsonResp.streamHash, {
                  method: 'POST',
                  body: data
                });
            });
        });
    }
  }
});