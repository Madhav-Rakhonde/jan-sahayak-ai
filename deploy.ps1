$VPS_IP = "YOUR_VPS_IP" # <-- CHANGE THIS TO YOUR ACTUAL VPS IP
$VPS_USER = "root"
$JAR_PATH = "c:\Users\Madhav\Desktop\Springboot project\AI\AI\target\AI-0.0.1-SNAPSHOT.jar"
$REMOTE_PATH = "/opt/govlyx-backend.jar"
Write-Host "Starting Maven Build..." -ForegroundColor Cyan
mvn clean package "-Dmaven.test.skip=true"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed! Aborting deployment." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "Build successful! Uploading JAR to VPS ($VPS_IP)..." -ForegroundColor Cyan
scp $JAR_PATH ${VPS_USER}@${VPS_IP}:${REMOTE_PATH}
if ($LASTEXITCODE -ne 0) {
    Write-Host "Upload failed! Check your SSH connection." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "Upload complete!" -ForegroundColor Green
Write-Host "Now log into your VPS and run: sudo systemctl restart govlyx-backend" -ForegroundColor Yellow
