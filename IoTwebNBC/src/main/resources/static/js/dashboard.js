// Dashboard JavaScript Functionality

// Chart instances
let tempHumidityChart;

// Initialize charts when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    initializeCharts();
    startRealTimeUpdates();
});

// Initialize Temperature & Humidity Chart
function initializeCharts() {
    // Prefer canvas id used in template
    const tempCanvas = document.getElementById('tempHumChart') || document.getElementById('tempHumidityChart');
    if (tempCanvas) {
        // If a Chart already exists on this canvas (created by inline template script), reuse it
        const existing = Chart.getChart(tempCanvas);
        if (existing) {
            tempHumidityChart = existing;
            return;
        }

        const ctx = tempCanvas.getContext('2d');
        tempHumidityChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [
                    { label: 'Temperature (°C)', data: [], borderColor: '#ff6b6b', backgroundColor: 'rgba(255,107,107,0.1)', tension: 0.4, pointRadius: 4, yAxisID: 'left' },
                    { label: 'Humidity (%)',     data: [], borderColor: '#4ecdc4', backgroundColor: 'rgba(78,205,196,0.1)', tension: 0.4, pointRadius: 4, yAxisID: 'left' },
                    { label: 'Light (Lux)',      data: [], borderColor: '#f39c12', backgroundColor: 'rgba(243,156,18,0.1)', tension: 0.4, pointRadius: 4, yAxisID: 'right' }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: { grid: { color: 'rgba(0,0,0,0.06)' } },
                    left: { type: 'linear', position: 'left', beginAtZero: false, grid: { drawOnChartArea: true } },
                    right: { type: 'linear', position: 'right', beginAtZero: false, grid: { drawOnChartArea: false } }
                },
                plugins: { legend: { position: 'top' } }
            }
        });
    }
}

// Device Toggle Function
function toggleDevice(element, deviceType) {
    const statusIndicator = element.parentElement.querySelector('.status-indicator');
    const isActive = element.classList.contains('active');
    
    if (isActive) {
        element.classList.remove('active');
        statusIndicator.textContent = 'Off';
        statusIndicator.className = 'status-indicator status-off';
    } else {
        element.classList.add('active');
        statusIndicator.textContent = 'On';
        statusIndicator.className = 'status-indicator status-on';
    }
    
    // Here you can add API calls to control actual devices
    console.log(`${deviceType} turned ${isActive ? 'off' : 'on'}`);
    
    // Example API call (uncomment when backend is ready)
    // sendDeviceCommand(deviceType, !isActive);
}

// Send device command to backend (for future use)
function sendDeviceCommand(deviceType, isOn) {
    fetch('/api/device/control', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            deviceType: deviceType,
            status: isOn ? 'ON' : 'OFF'
        })
    })
    .then(response => response.json())
    .then(data => {
        console.log('Device command sent:', data);
    })
    .catch(error => {
        console.error('Error sending device command:', error);
    });
}

// Real-time data updates
function startRealTimeUpdates() {
    setInterval(() => {
        updateChartData();
    }, 5000); // Update every 5 seconds
}

// Update chart data with new values
function updateChartData() {
    if (!tempHumidityChart) return;
    // Simulate or push a combined point: [temp, hum, light]
    const now = new Date();
    const timeString = now.toTimeString().slice(0, 8);
    const temp = Math.floor(Math.random() * 5) + 28;
    const hum = Math.floor(Math.random() * 10) + 65;
    const light = Math.floor(Math.random() * 100) + 700;

    // pushPoint behaviour: add label and values for each dataset
    tempHumidityChart.data.labels.push(timeString);
    tempHumidityChart.data.datasets[0].data.push(temp);
    tempHumidityChart.data.datasets[1].data.push(hum);
    tempHumidityChart.data.datasets[2].data.push(light);

    // trim
    const MAX_POINTS = 10;
    if (tempHumidityChart.data.labels.length > MAX_POINTS) {
        tempHumidityChart.data.labels.shift();
        tempHumidityChart.data.datasets.forEach(ds => ds.data.shift());
    }
    tempHumidityChart.update('none');
}

// Update sensor card values (for future real-time updates)
function updateSensorValues(temperature, humidity, light) {
    const tempElement = document.querySelector('.sensor-card.temperature .sensor-value');
    const humElement = document.querySelector('.sensor-card.humidity .sensor-value');
    const lightElement = document.querySelector('.sensor-card.light .sensor-value');
    
    if (tempElement) tempElement.textContent = `${temperature}°C`;
    if (humElement) humElement.textContent = `${humidity}%`;
    if (lightElement) lightElement.textContent = `${light} Lux`;
}

// Fetch real sensor data from backend (for future use)
function fetchSensorData() {
    fetch('/dashboard/api/sensor-data')
        .then(response => response.json())
        .then(data => {
            if (data && data.length > 0) {
                const latestData = data[0]; // Get the most recent data
                updateSensorValues(latestData.temperature, latestData.humidity, latestData.lightLevel);
                
                // Update charts with real data if available
                updateChartsWithRealData(data);
            }
        })
        .catch(error => {
            console.error('Error fetching sensor data:', error);
        });
}

// Update charts with real sensor data
function updateChartsWithRealData(sensorData) {
    if (!tempHumidityChart || !sensorData || sensorData.length === 0) return;
    const sliced = sensorData.slice(0, 10);
    const timeLabels = sliced.map(item => {
        const date = new Date(item.timestamp);
        return date.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
    });
    const tempData = sliced.map(item => item.temperature);
    const humData = sliced.map(item => item.humidity);
    const lightData = sliced.map(item => item.lightLevel);

    tempHumidityChart.data.labels = timeLabels;
    tempHumidityChart.data.datasets[0].data = tempData;
    tempHumidityChart.data.datasets[1].data = humData;
    tempHumidityChart.data.datasets[2].data = lightData;
    tempHumidityChart.update();
}

// Export functions for global use
// Avoid clobbering the inline toggleDevice defined in the Thymeleaf template.
// Export under a different name for the static UI module.
window.toggleDeviceUI = toggleDevice;
window.fetchSensorData = fetchSensorData;
