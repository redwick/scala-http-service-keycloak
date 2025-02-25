package server.app

import io.circe.syntax.EncoderOps


trait Codes {
  object EnvCodes{
    val LicenseNotSet = "license is not set"
    val AWSAccessKeyNotSet = "aws_accessKey is not set"
    val AWSSecretKeyNotSet = "aws_secretKey is not set"
    val SSOClientIdNotSet = "sso_clientId is not set"
    val SSOSecretKeyNotSet = "sso_secretKey is not set"
    val PGHostNotSet = "pg_host is not set"
    val PGPassNotSet = "pg_pass is not set"
    val MailLoginNotSet = "mail_login is not set"
    val MailPasswordNotSet = "mail_password is not set"
  }
  object ErrorCodes  {
    val UnreachableCode = "Unreachable code detected."
    val InvalidAuthenticationType = "Invalid authentication type"
    val BearerTokenNotProvided  = "Bearer Token not provided"
    val TokenExpiredOrInvalid = "Token expired or invalid"
    val UserDoesntHaveRole = "User doesn't have required role"
    val UnidentifiedMessageFromActor = "Unidentified message received from actor"
  }
  object TextCodes{
    val Undefined: String = "undefined".asJson.noSpaces
    val Removed: String = "removed".asJson.noSpaces
    val Updated: String = "updated".asJson.noSpaces
    val Outdated: String = "outdated".asJson.noSpaces
    val NotFound: String = "notfound".asJson.noSpaces
    val AlreadyExists: String = "already_exists".asJson.noSpaces
    val Forwarded: String = "forwarded".asJson.noSpaces
    val TokenNotValid: String = "token_not_valid".asJson.noSpaces
    val TokenNotFound: String = "token_not_found".asJson.noSpaces
    val ForwardedWithRoles: String = "forwarded with rolesRequired".asJson.noSpaces
    val CodeError: String = "CODE_ERROR".asJson.noSpaces
    val OK: String = "OK".asJson.noSpaces
    val VerifyEmailCodeSubject = "Email Verification Code"
    val GreetingEmailSubject = "Greeting Email"
  }
  object TextCodesRu{
    val NoToken: String = "Отстутвует Bearer Token".asJson.noSpaces
    val TokenExpiredOrInvalid: String = "Bearer Token просрочен или недействителен".asJson.noSpaces
    val ServerError: String = "Ошибка сервера".asJson.noSpaces
    val DrawingAlreadyExists: String = "Такой чертёж уже существует".asJson.noSpaces
    val FileInDirectoryExists: String = "Файл или папка с таким именем уже присутствует в директории".asJson.noSpaces
    val DateFromMustBeBeforeDateTo: String = "Дата начала должна быть раньше".asJson.noSpaces
    val Outdated: String = "Состояние отправляемого объекта отличается от состояния в базе данных. Необходимо обновить объект перед повторной отправкой".asJson.noSpaces
    val NotFound: String = "Изменяемый объект не найден в базе данных".asJson.noSpaces
    val UserRegistered: String = "Вы успешно зарегистрировались в системе".asJson.noSpaces
    val PasswordNotValid: String = "Пароль не удовлетворяет требованиям. Минимальная длина пароля 8 символов, должен содержать только цифры и латинские буквы, минимум одна буква, минимум одна цифра".asJson.noSpaces
    val EmailNotVerified: String = "Ваша электронная почта не подтверждена".asJson.noSpaces
    val EmailIsNotValid: String = "Ваша электронная почта недействительна".asJson.noSpaces
    val EmailAlreadyRegistered: String = "Пользователь с указанным email уже зарегистрирован".asJson.noSpaces
    val VerifyCodeHasBeenSend: String = "Код подтверждения отправлен на email. Проверьте чтобы письмо не попало в спам".asJson.noSpaces
    val VerifyCodeNotValid: String = "Код подтверждения недействителен".asJson.noSpaces
    val UndefinedWalletKind: String = "Неизвестный тип действия с кошельком".asJson.noSpaces
    val InsufficientWalletFunds: String = "Недостаточное количество средств на кошельке".asJson.noSpaces
    val UndefinedContract: String = "Указанный контракт не найден".asJson.noSpaces
    val ContractPriceNotMatch: String = "Стоимость операции не соответствует стоимости контракта".asJson.noSpaces
    val ContractAlreadyPayed: String = "По указанному контракту уже произведена оплата".asJson.noSpaces
  }
  object MessagesRu{
    val YourVerificationCode: String = "Ваш код подтверждения электронной почты:"
    val ValidIn24h: String = "Код подтверждения действителен 24 часа"
    val GreetingEmail: String = "Вы успешно зарегистрировались в системе DJARVISS. Теперь вы можете зайти на сайт https://djarviss.ru используя ваши логин и пароль. Логином является адрес вашей почты."
  }
}
