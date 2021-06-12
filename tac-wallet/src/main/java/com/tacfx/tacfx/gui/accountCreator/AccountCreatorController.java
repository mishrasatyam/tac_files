package com.tacfx.tacfx.gui.accountCreator;

import com.tacfx.tacfx.bus.RxBus;
import com.tacfx.tacfx.gui.MasterController;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class AccountCreatorController extends MasterController {
    protected final AccountCreator accountCreator;

    @FXML protected Button backButton;
    @FXML protected Button nextButton;

    AccountCreatorController(final RxBus rxBus, final AccountCreator accountCreator) {
        super(rxBus);
        this.accountCreator = accountCreator;
    }
}
