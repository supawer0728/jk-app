package com.jkapp.todo

class FakeTodoReminderScheduler : TodoReminderScheduler {
    val scheduled = mutableListOf<TodoItem>()
    val cancelled = mutableListOf<TodoItem>()

    override fun schedule(item: TodoItem) {
        scheduled.add(item)
    }

    override fun cancel(item: TodoItem) {
        cancelled.add(item)
    }
}
