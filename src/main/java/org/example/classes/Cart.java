package org.example.classes;

import org.example.generator.Generatable;

import java.util.List;

@Generatable
public class Cart {
    private List<Product> items;

    public Cart(List<Product> items) {
        this.items = items;
    }

    public List<Product> getItems() {
        return items;
    }

    @Override
    public String toString() {
        return "List of items: " + items;
    }

    public void setItems(List<Product> items) {
        this.items = items;
    }

    // Конструктор, методы добавления и удаления товаров, геттеры и другие методы
}