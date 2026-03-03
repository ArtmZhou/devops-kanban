<template>
  <div id="app">
    <nav class="navbar">
      <router-link to="/">Kanban Board</router-link>
      <router-link to="/task-sources">Task Sources</router-link>
      <router-link to="/agents">Agents</router-link>
    </nav>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
  min-height: 100vh;
  background-color: #f5f5f5;
}

.navbar {
  display: flex;
  gap: 1rem;
  padding: 1rem 2rem;
  background-color: #2c3e50;
}

.navbar a {
  color: #ecf0f1;
  text-decoration: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.navbar a:hover {
  background-color: #34495e;
}

.navbar a.router-link-active {
  background-color: #3498db;
}

.main-content {
  padding: 1rem;
}
</style>
