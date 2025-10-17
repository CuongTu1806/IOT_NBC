document.addEventListener('DOMContentLoaded', function() {
    const deviceSelect = document.getElementById('deviceSelect');
    const sensorThresholdContainer = document.getElementById('sensorThresholdContainer');
    const selectedSensors = document.getElementById('selectedSensors');
    const searchInput = document.getElementById('searchInput');
    const inputGuide = document.getElementById('inputGuide');
    
    let selectedSensorList = [];

    // Xử lý khi chọn device từ dropdown
    deviceSelect.addEventListener('change', function() {
        const selectedDevice = this.value;
        if (selectedDevice && !selectedSensorList.includes(selectedDevice)) {
            addSensor(selectedDevice);
            this.value = ''; // Reset dropdown
        }
    });

    // Thêm sensor vào danh sách
    function addSensor(sensorType) {
        if (selectedSensorList.includes(sensorType)) return;
        
        selectedSensorList.push(sensorType);
        renderSelectedSensors();
        updateInputGuide();
    }

    // Xóa sensor khỏi danh sách
    function removeSensor(sensorType) {
        selectedSensorList = selectedSensorList.filter(s => s !== sensorType);
        renderSelectedSensors();
        updateInputGuide();
    }
    
    // Make removeSensor globally available
    window.removeSensor = removeSensor;

    // Render danh sách sensor đã chọn
    function renderSelectedSensors() {
        if (selectedSensorList.length === 0) {
            sensorThresholdContainer.style.display = 'none';
            return;
        }
        
        sensorThresholdContainer.style.display = 'block';
        selectedSensors.innerHTML = '';
        
        selectedSensorList.forEach(sensorType => {
            const sensorItem = createSensorItem(sensorType);
            selectedSensors.appendChild(sensorItem);
        });
    }

    // Tạo element cho sensor item
    function createSensorItem(sensorType) {
        const div = document.createElement('div');
        div.className = 'sensor-item';
        div.setAttribute('data-sensor', sensorType);
        
        const sensorNames = {
            'temperature': 'Temperature',
            'humidity': 'Humidity',
            'light': 'Light',
            'time': 'Time'
        };
        
        div.innerHTML = `
            <div class="sensor-label">
                <span class="sensor-name">${sensorNames[sensorType]}</span>
            </div>
            <select name="thresholdOps[${sensorType}]" class="threshold-select">
                <option value="">Threshold</option>
                <option value="=">Equal (=)</option>
                <option value=">">Greater than (>)</option>
                <option value="<">Less than (<)</option>
                <option value=">=">Greater or equal (>=)</option>
                <option value="<=">Less or equal (<=)</option>
                <option value="range">Range (min-max)</option>
            </select>
            <button type="button" class="remove-sensor" onclick="removeSensor('${sensorType}')">×</button>
        `;
        
        return div;
    }

    // Cập nhật hướng dẫn input dựa trên thiết bị được chọn
    function updateInputGuide() {
        if (selectedSensorList.length === 0) {
            inputGuide.classList.remove('show');
            searchInput.placeholder = 'Chọn thiết bị để hiển thị hướng dẫn nhập';
            return;
        }

        let guideText = '';
        let placeholder = '';

        // Xử lý time sensor riêng
        const timeSensor = selectedSensorList.includes('time');
        const otherSensors = selectedSensorList.filter(s => s !== 'time');

        if (timeSensor) {
            guideText = 'Định dạng thời gian: YYYY-MM-DD hh:mm:ss<br>';
            placeholder = 'Ví dụ: 2024-01-15 14:30:00';
        }

        // Xử lý các sensor khác
        if (otherSensors.length > 0) {
            const sensorNames = {
                'temperature': 'Nhiệt độ',
                'humidity': 'Độ ẩm', 
                'light': 'Ánh sáng'
            };

            const sensorInfo = otherSensors.map(s => ({
                name: sensorNames[s] || s,
                sensor: s
            }));

            if (sensorInfo.length > 0) {
                if (timeSensor) guideText += '<br>';

                guideText += `Nhập giá trị cho ${sensorInfo.map(info => info.name).join(', ')}<br>`;
                
                // Tạo placeholder
                const placeholders = sensorInfo.map(() => '25');
                placeholder = timeSensor ? 
                    `${placeholder} | ${placeholders.join(', ')}` : 
                    placeholders.join(', ');

                // Thêm hướng dẫn format
                if (sensorInfo.length > 1) {
                    guideText += 'Các giá trị cách nhau bằng dấu phẩy (,)<br>';
                    guideText += 'Thứ tự: ' + sensorInfo.map(info => info.name).join(' → ');
                }
            }
        }

        inputGuide.innerHTML = guideText;
        inputGuide.classList.add('show');
        searchInput.placeholder = placeholder;
    }

    // Xử lý khi thay đổi threshold (delegate event)
    selectedSensors.addEventListener('change', function(e) {
        if (e.target.classList.contains('threshold-select')) {
            updateInputGuide();
        }
    });

    // Xử lý khi submit form - parse input search thành threshold values
    const form = document.querySelector('.filter-bar');
    if (form) {
        form.addEventListener('submit', function(e) {
            // Comment phần phức tạp để test giao diện
            console.log('Form submitted - testing UI only');
            
            // const selectedSensors = Array.from(sensorCheckboxes)
            //     .filter(checkbox => checkbox.checked)
            //     .map(checkbox => {
            //         const sensorItem = checkbox.closest('.sensor-item');
            //         const thresholdSelect = sensorItem.querySelector('.threshold-select');
            //         return {
            //             sensor: checkbox.value,
            //             threshold: thresholdSelect.value
            //         };
            //     });

            // if (selectedSensors.length > 0 && searchInput.value.trim()) {
            //     const inputValue = searchInput.value.trim();
            //     const values = inputValue.split(',').map(v => v.trim());
                
            //     // Tạo hidden inputs cho threshold values
            //     selectedSensors.forEach((sensorInfo, index) => {
            //         if (sensorInfo.threshold && values[index]) {
            //             // Remove existing hidden input if any
            //             const existingInput = document.querySelector(`input[name="thresholdValues[${sensorInfo.sensor}]"]`);
            //             if (existingInput) {
            //                 existingInput.remove();
            //             }
                    
            //             // Create new hidden input
            //             const hiddenInput = document.createElement('input');
            //             hiddenInput.type = 'hidden';
            //             hiddenInput.name = `thresholdValues[${sensorInfo.sensor}]`;
            //             hiddenInput.value = values[index];
            //             form.appendChild(hiddenInput);
            //         }
            //     });
            // }
        });
    }

    // Xử lý khi focus vào input
    searchInput.addEventListener('focus', function() {
        if (inputGuide.innerHTML) {
            inputGuide.classList.add('show');
        }
    });

    // Xử lý khi blur khỏi input
    searchInput.addEventListener('blur', function() {
        // Delay để cho phép click vào guide
        setTimeout(() => {
            inputGuide.classList.remove('show');
        }, 200);
    });

    // Khởi tạo trạng thái ban đầu
    updateThresholdStates();
    updateInputGuide();

    // Xử lý sắp xếp bảng
    const table = document.getElementById('sensorTable');
    const headers = table.querySelectorAll('th');
    
    headers.forEach((header, index) => {
        header.addEventListener('click', function() {
            // Logic sắp xếp bảng (có thể implement sau)
            console.log('Sort by column:', index);
        });
    });

    // Xử lý pagination
    const paginationButtons = document.querySelectorAll('.pagination button');
    paginationButtons.forEach(button => {
        button.addEventListener('click', function() {
            if (!this.disabled) {
                // Logic pagination (có thể implement sau)
                console.log('Pagination clicked');
            }
        });
    });

    // Xử lý thay đổi số dòng hiển thị
    const rowCountSelect = document.getElementById('rowCountSelect');
    if (rowCountSelect) {
        rowCountSelect.addEventListener('change', function() {
            // Logic thay đổi số dòng (có thể implement sau)
            console.log('Row count changed to:', this.value);
        });
    }

    // Hiện/ẩn dropdown khi click nút
    document.getElementById('sensorDropdownBtn').addEventListener('click', function(e) {
        e.stopPropagation();
        const dropdown = document.getElementById('sensorDropdown');
        dropdown.style.display = dropdown.style.display === 'block' ? 'none' : 'block';
    });

    // Ẩn dropdown khi click ra ngoài
    document.addEventListener('click', function(e) {
        document.getElementById('sensorDropdown').style.display = 'none';
    });

    // Không ẩn khi click bên trong dropdown
    document.getElementById('sensorDropdown').addEventListener('click', function(e) {
        e.stopPropagation();
    });

    document.querySelectorAll('.sensor-checkbox').forEach(function(checkbox) {
        checkbox.addEventListener('change', function() {
            const select = this.closest('.sensor-row').querySelector('.threshold-operator');
            select.disabled = !this.checked;
        });
    });
});
