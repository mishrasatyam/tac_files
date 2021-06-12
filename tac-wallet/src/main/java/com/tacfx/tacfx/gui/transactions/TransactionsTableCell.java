package com.tacfx.tacfx.gui.transactions;

import com.tacfx.tacfx.logic.TransactionDetails;
import com.tacfx.tacfx.logic.TransactionSummary;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.VBox;

public class TransactionsTableCell extends TableCell<TransactionDetails, TransactionSummary> {
    private final VBox vBox = new VBox();
    private final Label infoLabel = new Label();
    private final Label headlineLabel = new Label();

    public TransactionsTableCell() {
        vBox.getStyleClass().add("transactions-vbox");
        headlineLabel.getStyleClass().add("transactions-headline");
        infoLabel.getStyleClass().add("transactions-info");
        vBox.getChildren().addAll(headlineLabel, infoLabel);
    }

    @Override
    public void updateItem(TransactionSummary item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setText(null);
            setGraphic(null);
        } else {
            headlineLabel.setText(item.getHeadline());
            infoLabel.setText(item.getInfo());
            setGraphic(vBox);
        }
    }
}
