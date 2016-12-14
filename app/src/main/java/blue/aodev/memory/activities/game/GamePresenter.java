package blue.aodev.memory.activities.game;

import android.graphics.Point;
import android.os.Handler;
import android.support.annotation.NonNull;

import blue.aodev.memory.data.api.ApiEndpoints;
import blue.aodev.memory.data.api.GoalItem;
import blue.aodev.memory.data.api.GoalResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GamePresenter implements GameContract.Presenter {

    private static final int GOAL_ID = 470272;

    private GameContract.View view;

    /** The data of the GOAL_ID goal from the iKnow API **/
    private GoalResponse data;

    /** The state of the board **/
    private Card[][] board;

    /** The index of the card flipped first during a two cards flip **/
    private Point firstFlippedPos;

    public GamePresenter(@NonNull GameContract.View view) {
        this.view = view;
        view.setPresenter(this);
    }

    @Override
    public void start() {
        if (board == null) {
            loadData();
        }
    }

    @Override
    public void retryLoading() {
        loadData();
    }

    private void loadData() {
        view.showLoading();

        //FIXME this logic should be in a separate class
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ApiEndpoints.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiEndpoints apiService = retrofit.create(ApiEndpoints.class);
        Call<GoalResponse> call = apiService.getGoal(GOAL_ID);
        call.enqueue(new Callback<GoalResponse>() {
            @Override
            public void onResponse(Call<GoalResponse> call, Response<GoalResponse> response) {
                data = response.body();
                //TODO check if the view is still available
                newGame();
            }

            @Override
            public void onFailure(Call<GoalResponse> call, Throwable t) {
                view.showLoadingError();
            }
        });
    }

    @Override
    public void newGame() {
        // 6 by 2 for now
        int rowCount = 6;
        int columnCount = 2;

        int targetSize = columnCount*rowCount;
        int size = Math.min(data.getGoalItems().length, targetSize/2);
        view.setBoardSize(rowCount, columnCount);

        board = new Card[6][4];

        for (int i = 0; i < size; i++) {
            GoalItem.Item item = data.getGoalItems()[i].getItem();
            Card cueCard = new Card(item.getId(), item.getCue().getText(), Card.Type.CUE);
            Card responseCard =
                    new Card(item.getId(), item.getResponse().getText(), Card.Type.RESPONSE);

            int cueIndex = 2*i;
            int responseIndex = 2*i+1;
            board[cueIndex/columnCount][cueIndex%columnCount] = cueCard;
            board[responseIndex/columnCount][responseIndex%columnCount] = responseCard;
        }

        view.showGame();
        displayBoard();
    }

    private void displayBoard() {
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[i].length; j++) {
                displayCard(i, j);
            }
        }
    }

    private void displayCard(int row, int column) {
        Card card = board[row][column];
        if (card == null) {
            return;
        }
        if (card.isFlipped()) {
            view.showCard(row, column, card.getType(), card.getText());
        } else {
            view.hideCard(row, column, card.getType());
        }
    }

    @Override
    public void selectCard(int row, int column) {
        Card flipping = board[row][column];

        if (flipping.isFlipped()) {
            // We can't flip cards that are already flipped
            return;
        }

        // We flip the card to show it to the user
        flipping.flip();
        displayCard(row, column);

        if (firstFlippedPos == null) {
            // It's the first card we flip, just store it
            firstFlippedPos = new Point(row, column);
        } else {
            Card firstFlipped = board[firstFlippedPos.x][firstFlippedPos.y];
            if (firstFlipped.getItemId() == flipping.getItemId()) {
                // We have a match! We keep the two cards flipped
                firstFlippedPos = null;

                //TODO check if it was the last one
            } else {
                // We flip back the two cards after some time
                final Point pos1 = firstFlippedPos;
                final Point pos2 = new Point(row, column);
                firstFlippedPos = null;
                flipping.flip();
                firstFlipped.flip();

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //TODO check if the view is still available
                        displayCard(pos1.x, pos1.y);
                        displayCard(pos2.x, pos2.y);
                    }
                }, 1000);
            }
        }
    }
}
