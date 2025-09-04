-- Insert sample notification templates

-- Order confirmation email template
INSERT INTO notification_templates (
    name, display_name, description, type, channel, language,
    subject_template, body_template, html_template,
    variables, default_values, active, template_version,
    sender_name, sender_email, category, tags,
    created_at, updated_at
) VALUES (
    'order-confirmation', 'Order Confirmation', 'Template for order confirmation notifications',
    'ORDER_PLACED', 'EMAIL', 'en',
    'Order Confirmation - #{orderNumber}',
    'Dear [[${customerName}]],

Thank you for your order! We have received your order and it is being processed.

Order Details:
- Order Number: [[${orderNumber}]]
- Order Date: [[${orderDate}]]
- Total Amount: $[[${totalAmount}]]

Items Ordered:
[[# th:each="item : ${items}"]]
- [[${item.name}]] (Qty: [[${item.quantity}]]) - $[[${item.price}]]
[[/]]

Your order will be shipped to:
[[${shippingAddress}]]

We will send you another notification when your order ships.

Thank you for shopping with us!

Best regards,
The [[${companyName}]] Team',
    '<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Order Confirmation</title>
</head>
<body>
    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
        <h2>Order Confirmation</h2>
        <p>Dear <span th:text="${customerName}">Customer</span>,</p>
        
        <p>Thank you for your order! We have received your order and it is being processed.</p>
        
        <div style="background-color: #f5f5f5; padding: 15px; margin: 20px 0;">
            <h3>Order Details:</h3>
            <p><strong>Order Number:</strong> <span th:text="${orderNumber}">ORD-123</span></p>
            <p><strong>Order Date:</strong> <span th:text="${orderDate}">2024-01-01</span></p>
            <p><strong>Total Amount:</strong> $<span th:text="${totalAmount}">99.99</span></p>
        </div>
        
        <h3>Items Ordered:</h3>
        <table style="width: 100%; border-collapse: collapse;">
            <tr th:each="item : ${items}">
                <td style="padding: 8px; border-bottom: 1px solid #ddd;" th:text="${item.name}">Product Name</td>
                <td style="padding: 8px; border-bottom: 1px solid #ddd;">Qty: <span th:text="${item.quantity}">1</span></td>
                <td style="padding: 8px; border-bottom: 1px solid #ddd;">$<span th:text="${item.price}">29.99</span></td>
            </tr>
        </table>
        
        <div style="margin: 20px 0;">
            <h3>Shipping Address:</h3>
            <p th:text="${shippingAddress}">123 Main St, City, State 12345</p>
        </div>
        
        <p>We will send you another notification when your order ships.</p>
        
        <p>Thank you for shopping with us!</p>
        
        <p>Best regards,<br>
        The <span th:text="${companyName}">Company</span> Team</p>
    </div>
</body>
</html>',
    '{"customerName": "string", "orderNumber": "string", "orderDate": "string", "totalAmount": "number", "items": "array", "shippingAddress": "string", "companyName": "string"}',
    '{"companyName": "Our Store"}',
    true, 1,
    'Our Store', 'noreply@ourstore.com', 'order', 'order,confirmation,email',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Order shipped email template
INSERT INTO notification_templates (
    name, display_name, description, type, channel, language,
    subject_template, body_template, html_template,
    variables, default_values, active, template_version,
    sender_name, sender_email, category, tags,
    created_at, updated_at
) VALUES (
    'order-shipped', 'Order Shipped', 'Template for order shipped notifications',
    'ORDER_SHIPPED', 'EMAIL', 'en',
    'Your Order #{orderNumber} Has Shipped!',
    'Dear [[${customerName}]],

Great news! Your order has been shipped and is on its way to you.

Order Details:
- Order Number: [[${orderNumber}]]
- Tracking Number: [[${trackingNumber}]]
- Carrier: [[${carrier}]]
- Estimated Delivery: [[${estimatedDelivery}]]

You can track your package using the tracking number above on the [[${carrier}]] website.

Shipping Address:
[[${shippingAddress}]]

Thank you for your business!

Best regards,
The [[${companyName}]] Team',
    '<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Order Shipped</title>
</head>
<body>
    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
        <h2>Your Order Has Shipped!</h2>
        <p>Dear <span th:text="${customerName}">Customer</span>,</p>
        
        <p>Great news! Your order has been shipped and is on its way to you.</p>
        
        <div style="background-color: #e8f5e8; padding: 15px; margin: 20px 0;">
            <h3>Shipping Details:</h3>
            <p><strong>Order Number:</strong> <span th:text="${orderNumber}">ORD-123</span></p>
            <p><strong>Tracking Number:</strong> <span th:text="${trackingNumber}">TRK-456</span></p>
            <p><strong>Carrier:</strong> <span th:text="${carrier}">UPS</span></p>
            <p><strong>Estimated Delivery:</strong> <span th:text="${estimatedDelivery}">2024-01-05</span></p>
        </div>
        
        <p>You can track your package using the tracking number above on the <span th:text="${carrier}">carrier</span> website.</p>
        
        <div style="margin: 20px 0;">
            <h3>Shipping Address:</h3>
            <p th:text="${shippingAddress}">123 Main St, City, State 12345</p>
        </div>
        
        <p>Thank you for your business!</p>
        
        <p>Best regards,<br>
        The <span th:text="${companyName}">Company</span> Team</p>
    </div>
</body>
</html>',
    '{"customerName": "string", "orderNumber": "string", "trackingNumber": "string", "carrier": "string", "estimatedDelivery": "string", "shippingAddress": "string", "companyName": "string"}',
    '{"companyName": "Our Store"}',
    true, 1,
    'Our Store', 'noreply@ourstore.com', 'order', 'order,shipped,email',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Payment success email template
INSERT INTO notification_templates (
    name, display_name, description, type, channel, language,
    subject_template, body_template, html_template,
    variables, default_values, active, template_version,
    sender_name, sender_email, category, tags,
    created_at, updated_at
) VALUES (
    'payment-success', 'Payment Success', 'Template for successful payment notifications',
    'PAYMENT_SUCCESS', 'EMAIL', 'en',
    'Payment Confirmation - $[[${amount}]]',
    'Dear [[${customerName}]],

Your payment has been successfully processed.

Payment Details:
- Amount: $[[${amount}]]
- Payment Method: [[${paymentMethod}]]
- Transaction ID: [[${transactionId}]]
- Date: [[${paymentDate}]]

[[# th:if="${orderNumber}"]]
This payment is for Order #[[${orderNumber}]].
[[/]]

Thank you for your payment!

Best regards,
The [[${companyName}]] Team',
    '<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Payment Confirmation</title>
</head>
<body>
    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
        <h2>Payment Confirmation</h2>
        <p>Dear <span th:text="${customerName}">Customer</span>,</p>
        
        <p>Your payment has been successfully processed.</p>
        
        <div style="background-color: #e8f5e8; padding: 15px; margin: 20px 0;">
            <h3>Payment Details:</h3>
            <p><strong>Amount:</strong> $<span th:text="${amount}">99.99</span></p>
            <p><strong>Payment Method:</strong> <span th:text="${paymentMethod}">Credit Card</span></p>
            <p><strong>Transaction ID:</strong> <span th:text="${transactionId}">TXN-123</span></p>
            <p><strong>Date:</strong> <span th:text="${paymentDate}">2024-01-01</span></p>
        </div>
        
        <div th:if="${orderNumber}">
            <p>This payment is for Order #<span th:text="${orderNumber}">ORD-123</span>.</p>
        </div>
        
        <p>Thank you for your payment!</p>
        
        <p>Best regards,<br>
        The <span th:text="${companyName}">Company</span> Team</p>
    </div>
</body>
</html>',
    '{"customerName": "string", "amount": "number", "paymentMethod": "string", "transactionId": "string", "paymentDate": "string", "orderNumber": "string", "companyName": "string"}',
    '{"companyName": "Our Store"}',
    true, 1,
    'Our Store', 'noreply@ourstore.com', 'payment', 'payment,success,email',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- Welcome email template
INSERT INTO notification_templates (
    name, display_name, description, type, channel, language,
    subject_template, body_template, html_template,
    variables, default_values, active, template_version,
    sender_name, sender_email, category, tags,
    created_at, updated_at
) VALUES (
    'user-welcome', 'Welcome Email', 'Template for welcoming new users',
    'USER_REGISTRATION', 'EMAIL', 'en',
    'Welcome to [[${companyName}]]!',
    'Dear [[${customerName}]],

Welcome to [[${companyName}]]! We are excited to have you as part of our community.

Your account has been successfully created with the email address: [[${email}]]

Here are some things you can do to get started:
- Browse our products and services
- Set up your profile preferences
- Subscribe to our newsletter for exclusive offers

If you have any questions, please do not hesitate to contact our support team.

Thank you for joining us!

Best regards,
The [[${companyName}]] Team',
    '<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Welcome</title>
</head>
<body>
    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
        <h2>Welcome to <span th:text="${companyName}">Our Store</span>!</h2>
        <p>Dear <span th:text="${customerName}">Customer</span>,</p>
        
        <p>Welcome to <span th:text="${companyName}">Our Store</span>! We are excited to have you as part of our community.</p>
        
        <p>Your account has been successfully created with the email address: <strong th:text="${email}">user@example.com</strong></p>
        
        <div style="background-color: #f0f8ff; padding: 15px; margin: 20px 0;">
            <h3>Here are some things you can do to get started:</h3>
            <ul>
                <li>Browse our products and services</li>
                <li>Set up your profile preferences</li>
                <li>Subscribe to our newsletter for exclusive offers</li>
            </ul>
        </div>
        
        <p>If you have any questions, please do not hesitate to contact our support team.</p>
        
        <p>Thank you for joining us!</p>
        
        <p>Best regards,<br>
        The <span th:text="${companyName}">Company</span> Team</p>
    </div>
</body>
</html>',
    '{"customerName": "string", "email": "string", "companyName": "string"}',
    '{"companyName": "Our Store"}',
    true, 1,
    'Our Store', 'noreply@ourstore.com', 'account', 'welcome,registration,email',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
