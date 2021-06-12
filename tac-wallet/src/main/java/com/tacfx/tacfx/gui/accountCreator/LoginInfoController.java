package com.tacfx.tacfx.gui.accountCreator;

import com.tacfx.tacfx.bus.RxBus;
import com.tacfx.tacfx.config.ConfigService;
import com.tacfx.tacfx.encryption.Encrypter;
import com.tacfx.tacfx.gui.FXMLView;
import com.tacfx.tacfx.gui.login.LoginController;
import com.tacfx.tacfx.logic.FormValidator;
import com.tacfx.tacfx.logic.Profile;
import io.reactivex.Observable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginInfoController extends AccountCreatorController  {

    private ConfigService configService;

    @FXML private TextField usernameTextField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField passwordRepeatField;
    @FXML private Label accountExistsLabel;
    @FXML private Label weakPasswordLabel;
    @FXML private Label passwordsNotMatchLabel;

    public LoginInfoController(final RxBus rxBus, final AccountCreator accountCreator) {
        super(rxBus, accountCreator);
        rxBus.getConfigService().subscribe(configService -> this.configService = configService);
    }

    @FXML
	public void initialize() {
        final var usernameObservable = JavaFxObservable.valuesOf(usernameTextField.textProperty());
        final var passwordObservable = JavaFxObservable.valuesOf(passwordField.textProperty());
        final var passwordRepeatObservable = JavaFxObservable.valuesOf(passwordRepeatField.textProperty());

        usernameObservable.map(configService::profileExists)
                .subscribe(accountExistsLabel::setVisible);

        passwordObservable.map(this::passwordIsNotSafe)
                .subscribe(weakPasswordLabel::setVisible);

        Observable.merge(passwordObservable, passwordRepeatObservable)
                .map(observable -> !passwordField.getText().equals(passwordRepeatField.getText()))
                .subscribe(passwordsNotMatchLabel::setVisible);

        Observable.combineLatest(passwordObservable, passwordRepeatObservable, usernameObservable, this::validate)
                .subscribe(nextButton::setDisable);
    }

    @FXML
    void back(ActionEvent event) {
        switchRootScene(FXMLView.LOGIN, new LoginController(rxBus));
    }

    @FXML
    void next(ActionEvent event) throws Exception {
        configService.saveProfile(createProfile());
        switchRootScene(FXMLView.LOGIN, new LoginController(rxBus));
    }

    private Profile createProfile() throws Exception {
        return new Profile(usernameTextField.getText(),
                Encrypter.encrypt(accountCreator.getSeed(), passwordField.getText()), accountCreator.getNode(),
                accountCreator.isPrivateKeyAccount(), 0, 0, accountCreator.getNetworkId());
    }

    private Boolean validate(final String pw1, final String pw2, final String usr) {
        if (!pw1.equals(pw2) && !usr.isEmpty())
            return true;
        else if (pw1.isEmpty() || pw2.isEmpty() || usr.isEmpty())
            return true;
        else if (configService.profileExists(usr))
            return true;
        else
            return passwordIsNotSafe(pw1) || passwordIsNotSafe(pw2);
    }

    private boolean passwordIsNotSafe(String password) {
        return !FormValidator.isWellFormed(password, FormValidator.PASSWORD_PATTERN);
    }

}
