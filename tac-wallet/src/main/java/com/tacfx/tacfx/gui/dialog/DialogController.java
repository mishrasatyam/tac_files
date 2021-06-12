package com.tacfx.tacfx.gui.dialog;

import com.tacfx.tacfx.bus.RxBus;
import com.tacfx.tacfx.gui.MasterController;
import javafx.fxml.FXML;

class DialogController extends MasterController {
    DialogController(final RxBus rxBus) {
        super(rxBus);
    }

    @FXML
	public void initialize() {
    }

    @FXML
    void closeDialog() {
        getStage().close();
    }
}
