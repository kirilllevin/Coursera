function ret = cd1(rbm_w, visible_data)
% <rbm_w> is a matrix of size <number of hidden units> by <number of visible units>
% <visible_data> is a (possibly but not necessarily binary) matrix of size <number of visible units> by <number of data cases>
% The returned value is the gradient approximation produced by CD-1. It's of the same shape as <rbm_w>.

  % If the input data is non-binary, treat it as a probability distribution.
  visible_data = sample_bernoulli(visible_data);

  % Compute the hidden probabilities matrix, based on the input.
  hidden_probability = visible_state_to_hidden_probabilities(rbm_w, visible_data);

  % Sample the hidden units, conditional on the data above.
  hidden_sample = sample_bernoulli(hidden_probability);

  % Compute the visible probabilities matrix, based on the sample.
  visible_probability = hidden_state_to_visible_probabilities(rbm_w, hidden_sample);

  % Sample the reconstruction of the visible units, conditional on the data above.
  reconstruction = sample_bernoulli(visible_probability);

  % Update the hidden probabilities matrix again, based on the reconstruction.
  updated_hidden_probabilities = visible_state_to_hidden_probabilities(rbm_w, reconstruction);
  
  % Sample the updated hidden units, conditional on the data above.
  %updated_hidden_sample = sample_bernoulli(updated_hidden_probabilities);

  % Finally, compute the gradients.
  vi_hj_0 = configuration_goodness_gradient(visible_data, hidden_sample);  
  %vi_hj_1 = configuration_goodness_gradient(reconstruction, updated_hidden_sample);
  vi_hj_1 = configuration_goodness_gradient(reconstruction, updated_hidden_probabilities);
  
  % The returned value is just their difference.
  ret = vi_hj_0 - vi_hj_1;
end
