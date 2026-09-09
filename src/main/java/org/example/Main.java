package org.example;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting AI Manager...");

        ManagerAgent manager = new ManagerAgent();
        manager.delegateTask("Schedule a meeting for tomorrow at 10 AM");
    }
}
