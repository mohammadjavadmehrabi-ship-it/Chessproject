# Keep entry-point Activity and standard Android View constructors
-keep public class com.snakestudio.snake.MainActivity { *; }
-keep public class com.snakestudio.snake.SnakeGameView {
    public <init>(android.content.Context);
}
