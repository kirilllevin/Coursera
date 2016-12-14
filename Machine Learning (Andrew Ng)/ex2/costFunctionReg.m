function [J, grad] = costFunctionReg(theta, X, y, lambda)
%COSTFUNCTIONREG Compute cost and gradient for logistic regression with regularization
%   J = COSTFUNCTIONREG(theta, X, y, lambda) computes the cost of using
%   theta as the parameter for regularized logistic regression and the
%   gradient of the cost w.r.t. to the parameters. 

% Initialize some useful values
m = length(y); % number of training examples

% You need to return the following variables correctly 
J = 0;
grad = zeros(size(theta));

% ====================== YOUR CODE HERE ======================
% Instructions: Compute the cost of a particular choice of theta.
%               You should set J to the cost.
%               Compute the partial derivatives and set grad to the partial
%               derivatives of the cost w.r.t. each parameter in theta


sx = sigmoid(X * theta);
sub_theta = theta(2:size(theta));
reg = lambda / (2 * m) * sub_theta' * sub_theta;
J = 1 / m * sum((-1) * y' * log(sx) - (1 - y)' * log(1 - sx)) + reg;


d = sx - y;
grad = (1 / m) * X' * d + lambda / m * theta;

% Special case for the first partial derivative.
grad(1) = (1 / m) * X(:,1)' * d;






% =============================================================

end