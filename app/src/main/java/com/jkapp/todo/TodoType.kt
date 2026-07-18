package com.jkapp.todo

/**
 * 할일 계층 구분자. 1-depth 자식만 허용하며 자기참조(서브컬렉션) 없이 같은 컬렉션에 평탄하게 저장한다.
 * - MAIN: 독립 항목. 자식을 가질 수 있다.
 * - SUB: 자식 항목. mainTodoId로 부모를 참조하며, 자신이 다시 부모가 될 수 없다.
 */
enum class TodoType {
    MAIN,
    SUB;

    companion object {
        fun fromNameOrDefault(name: String?): TodoType =
            entries.firstOrNull { it.name == name } ?: MAIN
    }
}
