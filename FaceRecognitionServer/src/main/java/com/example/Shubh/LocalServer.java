package com.example.Shubh;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

public class LocalServer {
    private static final Gson gson = new Gson();
    private static final ConcurrentHashMap<String, String> pendingRequests = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> approvalStatus = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);

        server.createContext("/", new StaticFileHandler());
        server.createContext("/approval-request", new ApprovalRequestHandler());
        server.createContext("/approval-status", new ApprovalStatusHandler());
        server.createContext("/pending-requests", new PendingRequestsHandler());
        server.createContext("/respond", new RespondHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("Server started on http://localhost:8081");
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            try {
                String content = getStaticContent(path);
                String contentType = getContentType(path);

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, content.length());

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content.getBytes());
                }
            } catch (Exception e) {
                String response = "File not found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }

        private String getStaticContent(String path) throws IOException {
            if (path.equals("/index.html")) {
                return getIndexHtml();
            } else if (path.equals("/style.css")) {
                return getStyleCss();
            } else if (path.equals("/script.js")) {
                return getScriptJs();
            }
            throw new FileNotFoundException();
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            return "text/plain";
        }
    }

    static class ApprovalRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody());
                BufferedReader br = new BufferedReader(isr);
                String requestBody = br.readLine();

                JsonObject json = gson.fromJson(requestBody, JsonObject.class);
                String requestId = json.get("requestId").getAsString();
                String owner = json.get("owner").getAsString();
                String image = json.get("image").getAsString();

                pendingRequests.put(requestId, image);
                approvalStatus.put(requestId, "pending");

                String response = "Request received";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    static class ApprovalStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String requestId = path.substring(path.lastIndexOf("/") + 1);

            String status = approvalStatus.getOrDefault(requestId, "not_found");

            exchange.sendResponseHeaders(200, status.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(status.getBytes());
            }
        }
    }

    static class PendingRequestsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JsonObject response = new JsonObject();

            if (!pendingRequests.isEmpty()) {
                String requestId = pendingRequests.keys().nextElement();
                String image = pendingRequests.get(requestId);

                response.addProperty("requestId", requestId);
                response.addProperty("image", image);
                response.addProperty("hasPending", true);
            } else {
                response.addProperty("hasPending", false);
            }

            String jsonResponse = gson.toJson(response);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jsonResponse.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsonResponse.getBytes());
            }
        }
    }

    static class RespondHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody());
                BufferedReader br = new BufferedReader(isr);
                String requestBody = br.readLine();

                JsonObject json = gson.fromJson(requestBody, JsonObject.class);
                String requestId = json.get("requestId").getAsString();
                String response = json.get("response").getAsString();

                approvalStatus.put(requestId, response);
                pendingRequests.remove(requestId);

                String responseText = "Response recorded";
                exchange.sendResponseHeaders(200, responseText.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseText.getBytes());
                }
            }
        }
    }

    private static String getIndexHtml() {
    return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'><title>Smart Entry System</title><link rel='stylesheet' href='style.css'><link href='https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap' rel='stylesheet'><link href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css' rel='stylesheet'></head><body><div class='container'><header class='header'><div class='logo'><i class='fas fa-shield-alt'></i><h1>Smart Entry System</h1></div><div class='status-indicator' id='statusIndicator'><div class='pulse'></div><span>Waiting for Detection...</span></div></header><main id='content'><div class='waiting-state'><div class='scanner-animation'><div class='scanner-line'></div><div class='scanner-corners'><div class='corner tl'></div><div class='corner tr'></div><div class='corner bl'></div><div class='corner br'></div></div></div><h2>Face Detection System</h2><p>System ready. Waiting for face detection...</p></div></main></div><script src='script.js'></script></body></html>";
}

private static String getStyleCss() {
    return "*{margin:0;padding:0;box-sizing:border-box}body{font-family:'Inter',sans-serif;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);min-height:100vh;color:#333}.container{max-width:1200px;margin:0 auto;padding:20px;min-height:100vh;display:flex;flex-direction:column}.header{background:rgba(255,255,255,0.95);border-radius:20px;padding:25px;margin-bottom:30px;backdrop-filter:blur(10px);box-shadow:0 8px 32px rgba(0,0,0,0.1);display:flex;justify-content:space-between;align-items:center}.logo{display:flex;align-items:center;gap:15px}.logo i{font-size:2.5rem;color:#667eea}.logo h1{font-size:2.2rem;font-weight:700;background:linear-gradient(135deg,#667eea,#764ba2);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}.status-indicator{display:flex;align-items:center;gap:10px;padding:10px 20px;background:rgba(76,175,80,0.1);border:2px solid #4CAF50;border-radius:25px}.pulse{width:12px;height:12px;background:#4CAF50;border-radius:50%;animation:pulse 2s infinite}.status-indicator span{font-weight:500;color:#4CAF50}@keyframes pulse{0%{transform:scale(1);opacity:1}50%{transform:scale(1.2);opacity:0.7}100%{transform:scale(1);opacity:1}}main{flex:1;display:flex;align-items:center;justify-content:center}.waiting-state{text-align:center;background:rgba(255,255,255,0.95);border-radius:20px;padding:60px;backdrop-filter:blur(10px);box-shadow:0 8px 32px rgba(0,0,0,0.1);max-width:500px;width:100%}.scanner-animation{position:relative;width:200px;height:200px;margin:0 auto 40px;border:3px solid #e0e0e0;border-radius:15px;overflow:hidden}.scanner-line{position:absolute;top:0;left:0;width:100%;height:3px;background:linear-gradient(90deg,transparent,#667eea,transparent);animation:scan 2s linear infinite}.scanner-corners{position:absolute;top:0;left:0;right:0;bottom:0}.corner{position:absolute;width:30px;height:30px;border:3px solid #667eea}.corner.tl{top:-3px;left:-3px;border-right:none;border-bottom:none}.corner.tr{top:-3px;right:-3px;border-left:none;border-bottom:none}.corner.bl{bottom:-3px;left:-3px;border-right:none;border-top:none}.corner.br{bottom:-3px;right:-3px;border-left:none;border-top:none}@keyframes scan{0%{transform:translateY(0)}100%{transform:translateY(194px)}}.approval-container{background:rgba(255,255,255,0.98);border-radius:25px;padding:40px;backdrop-filter:blur(15px);box-shadow:0 15px 50px rgba(0,0,0,0.2);max-width:600px;width:100%;text-align:center}.person-info{background:linear-gradient(135deg,#f8f9fa,#e9ecef);border-radius:15px;padding:25px;margin:25px 0;border-left:5px solid #667eea}.info-row{display:flex;justify-content:space-between;align-items:center;margin:10px 0;padding:8px 0;border-bottom:1px solid rgba(0,0,0,0.1)}.info-label{font-weight:600;color:#555;display:flex;align-items:center;gap:8px}.info-value{font-weight:500;color:#333;background:rgba(255,255,255,0.8);padding:5px 12px;border-radius:8px}.face-image{max-width:100%;height:auto;border-radius:15px;box-shadow:0 8px 25px rgba(0,0,0,0.15);margin:20px 0;max-height:300px;object-fit:cover}.buttons{display:flex;gap:20px;justify-content:center;margin-top:30px}.btn{padding:15px 35px;border:none;border-radius:12px;font-size:16px;font-weight:600;cursor:pointer;transition:all 0.3s ease;display:flex;align-items:center;gap:10px;text-transform:uppercase;letter-spacing:0.5px}.approve{background:linear-gradient(135deg,#4CAF50,#45a049);color:white;box-shadow:0 6px 20px rgba(76,175,80,0.3)}.approve:hover{transform:translateY(-2px);box-shadow:0 8px 25px rgba(76,175,80,0.4)}.deny{background:linear-gradient(135deg,#f44336,#da190b);color:white;box-shadow:0 6px 20px rgba(244,67,54,0.3)}.deny:hover{transform:translateY(-2px);box-shadow:0 8px 25px rgba(244,67,54,0.4)}.response-message{background:rgba(255,255,255,0.95);border-radius:20px;padding:40px;text-align:center;backdrop-filter:blur(10px);box-shadow:0 8px 32px rgba(0,0,0,0.1);max-width:500px;width:100%}.response-message h2{font-size:2rem;margin-bottom:15px}.response-message.success{border-left:8px solid #4CAF50}.response-message.success h2{color:#4CAF50}.response-message.denied{border-left:8px solid #f44336}.response-message.denied h2{color:#f44336}.loading{display:inline-block;width:20px;height:20px;border:3px solid rgba(255,255,255,0.3);border-radius:50%;border-top-color:#fff;animation:spin 1s ease-in-out infinite}@keyframes spin{to{transform:rotate(360deg)}}";
}

private static String getScriptJs() {
    return "let currentRequestId=null;let scanCount=0;let lastProcessedRequestId=null;function getScanData(){if(scanCount===1){return{name:'Shubh Gupta',purpose:'Resident Entry',flatNumber:'A-101',isRented:true};}else if(scanCount===2){return{name:'Emma Johnson',purpose:'Guest meeting',flatNumber:'B-202',isRented:false};}else if(scanCount===3){return{name:'Michael Brown',purpose:'Delivery pickup',flatNumber:'C-301',isRented:false};}else{const names=['Sarah Davis','Robert Wilson','Lisa Anderson','David Martinez'];const purposes=['Maintenance visit','Document submission','Package collection','Inspection visit'];const flats=['D-102','A-203','B-104','C-205'];const index=(scanCount-4)%4;return{name:names[index],purpose:purposes[index],flatNumber:flats[index],isRented:Math.random()>0.5};}}function checkForRequests(){fetch('/pending-requests').then(response=>response.json()).then(data=>{if(data.hasPending){if(lastProcessedRequestId!==data.requestId){scanCount++;lastProcessedRequestId=data.requestId;showApprovalRequest(data.requestId,data.image);}}else{showWaiting();}}).catch(err=>console.error(err));}function showApprovalRequest(requestId,imageData){currentRequestId=requestId;const scanData=getScanData();const entryType=scanData.isRented?'Rented Person - Entry':'Non-Rented - Need Purpose';const statusIcon=scanData.isRented?'fas fa-home':'fas fa-user-plus';const statusColor=scanData.isRented?'#4CAF50':'#FF9800';document.getElementById('content').innerHTML=`<div class='approval-container'><h2><i class='${statusIcon}' style='color:${statusColor}'></i> ${entryType}</h2><img class='face-image' src='data:image/jpeg;base64,${imageData}' alt='Detected Face'/><div class='person-info'><div class='info-row'><span class='info-label'><i class='fas fa-user'></i> Name:</span><span class='info-value'>${scanData.name}</span></div><div class='info-row'><span class='info-label'><i class='fas fa-clipboard-list'></i> Purpose:</span><span class='info-value'>${scanData.purpose}</span></div><div class='info-row'><span class='info-label'><i class='fas fa-building'></i> Flat/Address:</span><span class='info-value'>${scanData.flatNumber}</span></div></div><div class='buttons'><button class='btn approve' onclick='respond(\"approved\")'><i class='fas fa-check'></i> APPROVE</button><button class='btn deny' onclick='respond(\"denied\")'><i class='fas fa-times'></i> DENY</button></div></div>`;updateStatus('Face Detected - Awaiting Decision','#FF9800');}function showWaiting(){document.getElementById('content').innerHTML=`<div class='waiting-state'><div class='scanner-animation'><div class='scanner-line'></div><div class='scanner-corners'><div class='corner tl'></div><div class='corner tr'></div><div class='corner bl'></div><div class='corner br'></div></div></div><h2>Face Detection System</h2><p>System ready. Waiting for face detection...</p></div>`;updateStatus('Waiting for Detection...','#4CAF50');}function respond(decision){if(!currentRequestId)return;const buttons=document.querySelectorAll('.btn');buttons.forEach(btn=>{btn.disabled=true;btn.innerHTML+=` <span class='loading'></span>`;});fetch('/respond',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({requestId:currentRequestId,response:decision})}).then(()=>{const isApproved=decision==='approved';const messageClass=isApproved?'success':'denied';const icon=isApproved?'fas fa-check-circle':'fas fa-times-circle';const message=isApproved?'ACCESS GRANTED':'ACCESS DENIED';const color=isApproved?'#4CAF50':'#f44336';document.getElementById('content').innerHTML=`<div class='response-message ${messageClass}'><h2><i class='${icon}'></i> ${message}</h2><p>Response sent successfully. Waiting for next request...</p></div>`;updateStatus(message,color);currentRequestId=null;setTimeout(()=>{checkForRequests();},3000);}).catch(err=>{console.error(err);buttons.forEach(btn=>{btn.disabled=false;btn.innerHTML=btn.innerHTML.replace(` <span class='loading'></span>`,'');});});}function updateStatus(text,color){const indicator=document.getElementById('statusIndicator');const span=indicator.querySelector('span');const pulse=indicator.querySelector('.pulse');span.textContent=text;pulse.style.background=color;indicator.style.borderColor=color;indicator.style.background=`${color}1A`;}setInterval(checkForRequests,1000);checkForRequests();";
}



}
