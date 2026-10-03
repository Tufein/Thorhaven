package nl.thorhaven.app;
interface IThorBridge {
 String movePackage(String packageName, int targetDisplay) = 0;
 String swapScreens(int topDisplay, int bottomDisplay) = 1;
 String deviceCall(String request) = 2;
 String currentApps(int topDisplay, int bottomDisplay) = 3;
 void destroy() = 16777114;
}
