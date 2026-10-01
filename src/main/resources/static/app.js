document.addEventListener("DOMContentLoaded", () => {
    const taskInput = document.getElementById("taskInput");
    const sendTaskBtn = document.getElementById("sendTaskBtn");
    const loadingIndicator = document.getElementById("loadingIndicator");
    const taskList = document.getElementById("taskList");

    // Fetch and display tasks immediately, then poll every 3 seconds
    fetchTasks();
    setInterval(fetchTasks, 3000);

    sendTaskBtn.addEventListener("click", () => {
        const desc = taskInput.value.trim();
        if (desc === "") return;

        taskInput.disabled = true;
        sendTaskBtn.disabled = true;
        loadingIndicator.classList.remove("hidden");

        // Kall backend med URL-param (avhengig av korleis the RestController er skrevet)
        fetch(`/api/task?taskDescription=${encodeURIComponent(desc)}`)
            .then(response => response.text())
            .then(data => {
                taskInput.value = "";
                // Force an immediate refresh
                fetchTasks();
            })
            .catch(error => console.error("Error creating task:", error))
            .finally(() => {
                taskInput.disabled = false;
                sendTaskBtn.disabled = false;
                loadingIndicator.classList.add("hidden");
            });
    });

    function fetchTasks() {
        fetch("/api/tasks")
            .then(response => response.json())
            .then(tasks => {
                renderTasks(tasks);
            })
            .catch(error => console.error("Error fetching tasks:", error));
    }

    function renderTasks(tasks) {
        // Clear list
        taskList.innerHTML = "";

        if (tasks.length === 0) {
            taskList.innerHTML = "<p>Ingen oppgåver funne.</p>";
            return;
        }

        // Sorter nyaste først (antar høgaste ID er nyast)
        tasks.sort((a, b) => b.id - a.id);

        tasks.forEach(task => {
            const taskCard = document.createElement("div");
            taskCard.className = "task-card";

            const header = document.createElement("div");
            header.innerHTML = `
                <h3>#${task.id} - ${task.description}</h3>
                <span class="status-badge ${task.status}">${task.status}</span>
            `;
            taskCard.appendChild(header);

            if (task.subTasks && task.subTasks.length > 0) {
                const subTasksDiv = document.createElement("div");
                subTasksDiv.className = "subtasks-container";

                const subTitle = document.createElement("h4");
                subTitle.textContent = "Deloppgåver (Delegerte til agentar):";
                subTasksDiv.appendChild(subTitle);

                task.subTasks.forEach(sub => {
                    const subItem = document.createElement("div");
                    subItem.className = "subtask-item";
                    subItem.innerHTML = `
                        <span>${sub.description}</span>
                        <span class="status-badge ${sub.status}">${sub.status}</span>
                    `;
                    subTasksDiv.appendChild(subItem);
                });

                taskCard.appendChild(subTasksDiv);
            }

            taskList.appendChild(taskCard);
        });
    }
});