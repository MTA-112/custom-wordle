import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WordleApp extends Application {

    private static final int WORD_LENGTH = 5;
    private static final int MAX_GUESSES = 6;
    private static final String WORDS_FILE = "words.txt";

    private Label[][] cells = new Label[MAX_GUESSES][WORD_LENGTH];
    private TextField guessField;
    private Label statusLabel;
    private Button guessButton;
    private Button newGameButton;

    private WordRepository wordRepo;
    private WordleEngine engine;
    private int currentRow = 0;

    @Override
    public void start(Stage stage) {
        wordRepo = WordRepository.fromFile(WORDS_FILE);
        if (wordRepo.isEmpty()) {
            // Fallback words if file not found or empty
            wordRepo = new WordRepository(List.of("APPLE", "CRANE", "WORLD", "CHAIR", "POINT"));
        }

        engine = new WordleEngine(wordRepo.randomWord(), MAX_GUESSES, WORD_LENGTH);

        GridPane grid = createGrid();
        HBox inputRow = createInputRow();
        statusLabel = new Label("Guess the 5-letter word!");

        VBox root = new VBox(10, grid, inputRow, statusLabel);
        root.setAlignment(Pos.CENTER);
        root.setPrefSize(400, 500);

        Scene scene = new Scene(root);
        stage.setTitle("Java Wordle");
        stage.setScene(scene);
        stage.show();
    }

    private GridPane createGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(5);
        grid.setAlignment(Pos.CENTER);

        for (int row = 0; row < MAX_GUESSES; row++) {
            for (int col = 0; col < WORD_LENGTH; col++) {
                Label cell = new Label("");
                cell.setPrefSize(45, 45);
                cell.setAlignment(Pos.CENTER);
                cell.setStyle(baseCellStyle());
                cells[row][col] = cell;
                grid.add(cell, col, row);
            }
        }
        return grid;
    }

    private HBox createInputRow() {
        guessField = new TextField();
        guessField.setPromptText("Enter 5-letter word");
        guessField.setPrefColumnCount(WORD_LENGTH);
        guessField.setOnAction(e -> handleGuess());

        guessButton = new Button("Guess");
        guessButton.setOnAction(e -> handleGuess());

        newGameButton = new Button("New Game");
        newGameButton.setOnAction(e -> startNewGame());

        HBox box = new HBox(10, guessField, guessButton, newGameButton);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void handleGuess() {
        if (engine.isGameOver()) {
            statusLabel.setText("Game over. Click 'New Game' to play again.");
            return;
        }

        String guess = guessField.getText().trim().toUpperCase();
        if (guess.length() != WORD_LENGTH) {
            statusLabel.setText("Please enter a " + WORD_LENGTH + "-letter word.");
            return;
        }

        if (!guess.matches("[A-Z]+")) {
            statusLabel.setText("Letters only, please.");
            return;
        }

        if (!wordRepo.isValidWord(guess)) {
            statusLabel.setText("Word not in list.");
            return;
        }

        GuessFeedback feedback;
        try {
            feedback = engine.guess(guess);
        } catch (IllegalStateException ex) {
            statusLabel.setText(ex.getMessage());
            return;
        }

        // Draw this guess on the current row
        for (int col = 0; col < WORD_LENGTH; col++) {
            char ch = feedback.getGuess().charAt(col);
            LetterState state = feedback.getStates()[col];

            Label cell = cells[currentRow][col];
            cell.setText(String.valueOf(ch));
            cell.setStyle(styleForState(state));
        }

        currentRow++;
        guessField.clear();

        if (engine.isWin()) {
            statusLabel.setText("You win! The word was: " + engine.getSecret());
            guessButton.setDisable(true);
            guessField.setDisable(true);
        } else if (engine.isGameOver()) {
            statusLabel.setText("Out of guesses! The word was: " + engine.getSecret());
            guessButton.setDisable(true);
            guessField.setDisable(true);
        } else {
            statusLabel.setText("Guesses left: " + (MAX_GUESSES - engine.getGuessesUsed()));
        }
    }

    private void startNewGame() {
        engine = new WordleEngine(wordRepo.randomWord(), MAX_GUESSES, WORD_LENGTH);
        currentRow = 0;
        guessField.clear();
        guessField.setDisable(false);
        guessButton.setDisable(false);
        statusLabel.setText("New game! Guess the 5-letter word.");

        for (int row = 0; row < MAX_GUESSES; row++) {
            for (int col = 0; col < WORD_LENGTH; col++) {
                Label cell = cells[row][col];
                cell.setText("");
                cell.setStyle(baseCellStyle());
            }
        }
    }

    private String baseCellStyle() {
        return "-fx-border-color: #444; -fx-border-width: 2px; " +
               "-fx-font-size: 20px; -fx-font-weight: bold; " +
               "-fx-background-color: #111; -fx-text-fill: white;";
    }

    private String styleForState(LetterState state) {
        String color;
        switch (state) {
            case CORRECT:
                color = "#6aaa64"; // green
                break;
            case PRESENT:
                color = "#c9b458"; // yellow
                break;
            case ABSENT:
            default:
                color = "#3a3a3c"; // gray
                break;
        }
        return "-fx-border-color: #444; -fx-border-width: 2px; " +
               "-fx-font-size: 20px; -fx-font-weight: bold; " +
               "-fx-background-color: " + color + "; -fx-text-fill: white;";
    }

    public static void main(String[] args) {
        launch(args);
    }
}

/* ====== OOP CORE CLASSES BELOW (no UI) ====== */

enum LetterState {
    CORRECT,   // right letter, right position
    PRESENT,   // right letter, wrong position
    ABSENT     // not in word
}

class GuessFeedback {
    private final String guess;
    private final LetterState[] states;

    public GuessFeedback(String guess, LetterState[] states) {
        this.guess = guess;
        this.states = states;
    }

    public String getGuess() {
        return guess;
    }

    public LetterState[] getStates() {
        return states;
    }
}

class WordleEngine {
    private final String secret;
    private final int maxGuesses;
    private final int wordLength;

    private int guessesUsed = 0;
    private boolean win = false;

    public WordleEngine(String secret, int maxGuesses, int wordLength) {
        this.secret = secret.toUpperCase();
        this.maxGuesses = maxGuesses;
        this.wordLength = wordLength;
    }

    public GuessFeedback guess(String guess) {
        if (isGameOver()) {
            throw new IllegalStateException("Game is already over.");
        }

        guess = guess.toUpperCase();
        if (guess.length() != wordLength) {
            throw new IllegalArgumentException("Guess must be length " + wordLength);
        }

        LetterState[] states = evaluate(secret, guess);
        guessesUsed++;

        if (guess.equals(secret)) {
            win = true;
        }

        return new GuessFeedback(guess, states);
    }

    private LetterState[] evaluate(String secret, String guess) {
        LetterState[] result = new LetterState[wordLength];
        int[] counts = new int[26];

        for (int i = 0; i < wordLength; i++) {
            char c = secret.charAt(i);
            counts[c - 'A']++;
        }

        // First pass: CORRECT
        for (int i = 0; i < wordLength; i++) {
            char g = guess.charAt(i);
            if (g == secret.charAt(i)) {
                result[i] = LetterState.CORRECT;
                counts[g - 'A']--;
            }
        }

        // Second pass: PRESENT or ABSENT
        for (int i = 0; i < wordLength; i++) {
            if (result[i] != null) continue;
            char g = guess.charAt(i);
            int idx = g - 'A';
            if (idx >= 0 && idx < 26 && counts[idx] > 0) {
                result[i] = LetterState.PRESENT;
                counts[idx]--;
            } else {
                result[i] = LetterState.ABSENT;
            }
        }

        return result;
    }

    public String getSecret() {
        return secret;
    }

    public int getGuessesUsed() {
        return guessesUsed;
    }

    public boolean isWin() {
        return win;
    }

    public boolean isGameOver() {
        return win || guessesUsed >= maxGuesses;
    }
}

class WordRepository {
    private final List<String> words;

    public WordRepository(List<String> words) {
        this.words = new ArrayList<>();
        for (String w : words) {
            this.words.add(w.toUpperCase());
        }
    }

    public static WordRepository fromFile(String filename) {
        List<String> result = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(Paths.get(filename))) {
                String word = line.trim().toUpperCase();
                if (word.length() == 5 && word.matches("[A-Z]+")) {
                    result.add(word);
                }
            }
        } catch (IOException e) {
            // ignore, will fall back
        }
        return new WordRepository(result);
    }

    public boolean isEmpty() {
        return words.isEmpty();
    }

    public boolean isValidWord(String word) {
        return words.contains(word.toUpperCase());
    }

    public String randomWord() {
        Random rand = new Random();
        return words.get(rand.nextInt(words.size()));
    }
}
